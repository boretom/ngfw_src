/**
 * $Id$
 */
package com.untangle.uvm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.NetcapManager;
import com.untangle.uvm.MetricManager;
import com.untangle.uvm.logging.SystemStatEvent;
import com.untangle.uvm.logging.InterfaceStatEvent;
import com.untangle.uvm.logging.LogEvent;
import com.untangle.uvm.network.NetworkSettings;
import com.untangle.uvm.network.InterfaceSettings;
import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.NodeManager;
import com.untangle.uvm.node.SessionTuple;
import com.untangle.uvm.node.NodeSettings;
import com.untangle.uvm.node.NodeMetric;
import com.untangle.uvm.util.Pulse;

public class MetricManagerImpl implements MetricManager
{
    private static final Pattern MEMINFO_PATTERN = Pattern.compile("(\\w+):\\s+(\\d+)\\s+kB");
    private static final Pattern VMSTAT_PATTERN = Pattern.compile("(\\w+)\\s+(\\d+)");
    private static final Pattern CPUINFO_PATTERN = Pattern.compile("([ 0-9a-zA-Z]*\\w)\\s*:\\s*(.*)$");
    private static final Pattern CPU_USAGE_PATTERN = Pattern.compile("cpu\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)");
    private static final Pattern NET_DEV_PATTERN = Pattern.compile("^\\s*([a-z0-9\\.]+\\d+):\\s*(\\d+)\\s+\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+(\\d+)");
    private static final Pattern DISK_STATS_PATTERN = Pattern.compile("\\s*\\d+\\s+\\d+\\s+[hs]d[a-zA-Z]+\\d+\\s+(\\d+)\\s+\\d+\\s+(\\d+)");

    private static final Logger logger = Logger.getLogger( MetricManagerImpl.class );

    private static final Set<String> MEMINFO_KEEPERS;
    private static final Set<String> VMSTAT_KEEPERS;
    private static final long FREQUENCY = 10*1000; /* 10 seconds */
    
    private final Pulse updatePulse = new Pulse("system-stat-collector", new SystemStatCollector(), FREQUENCY);

    private final Map<String,Long> rxtxBytesStore = new HashMap<String,Long>();

    private long lastNetDevUpdate = 0;

    private final Map<String,Long> diskRW0 = new HashMap<String,Long>();

    private long lastDiskUpdate = 0;

    private volatile Map<String, Object> systemStats = Collections.emptyMap();

    public MetricManagerImpl()
    {
    }

    protected void start()
    {
        updatePulse.start();
    }

    protected void stop()
    {
        updatePulse.stop();
    }

    public org.json.JSONObject getMetricsAndStats()
    {
        List<Long> nodeIds = new LinkedList<Long>();

        for ( Node node : UvmContextImpl.getInstance().nodeManager().nodeInstances() ) {
            nodeIds.add( node.getNodeSettings().getId() );
        }

        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("metrics", getMetrics( nodeIds ));
            json.put("systemStats", this.systemStats);
        } catch (Exception e) {
            logger.error( "Error generating metrics object", e );
        }
            
        return json;
    }

    public List<NodeMetric> getMetrics( Long nodeId )
    {
        Node node = UvmContextFactory.context().nodeManager().node( nodeId );
        if (node != null)
            return node.getMetrics();
        else {
            logger.warn("Node not found: " + nodeId, new Exception());
            return null;
        }
    }

    // private methods --------------------------------------------------------

    private Map<String, List<NodeMetric>> getMetrics( List<Long> nodeIds )
    {
        Map<String, List<NodeMetric>> stats = new HashMap<String, List<NodeMetric>>(nodeIds.size());

        for (Long nodeId : nodeIds) {
            stats.put( Long.toString(nodeId), getMetrics( nodeId ) );
        }

        return stats;
    }

    // private classes --------------------------------------------------------

    private class SystemStatCollector implements Runnable
    {
        private int  SYSTEM_STAT_LOG_DELAY = 55; // setting this to 55 means we'll log one every ~60 seconds
        private long ONE_BILLION = 1000000000l;
        private long lastLogTimeStamp = 0;

        private long user0 = 0;
        private long nice0 = 0;
        private long system0 = 0;
        private long idle0 = 0;

        public void run()
        {
            Map<String, Object> m = new HashMap<String, Object>();

            try {
                readMeminfo(m);
                readVmstat(m);
                readCpuinfo(m);
                readArchinfo(m);
                readLoadAverage(m);
                readUptime(m);
                getNumProcs(m);
                getCpuUsage(m);
                getNetDevUsage(m);
                getDiskUsage(m);

                long time = System.nanoTime();
                if ((time - lastLogTimeStamp)/ONE_BILLION >= SYSTEM_STAT_LOG_DELAY) {
                    SystemStatEvent sse = new SystemStatEvent();
                    sse.setMemTotal(Long.parseLong(m.get("MemTotal").toString()));
                    sse.setMemFree(Long.parseLong(m.get("MemFree").toString()));
                    sse.setMemCache(Long.parseLong(m.get("Cached").toString()));
                    sse.setMemCache(Long.parseLong(m.get("Buffers").toString()));
                    sse.setLoad1(Float.parseFloat(m.get("oneMinuteLoadAvg").toString()));
                    sse.setLoad5(Float.parseFloat(m.get("fiveMinuteLoadAvg").toString()));
                    sse.setLoad15(Float.parseFloat(m.get("fifteenMinuteLoadAvg").toString()));
                    sse.setCpuUser(Float.parseFloat(m.get("userCpuUtilization").toString()));
                    sse.setCpuSystem(Float.parseFloat(m.get("systemCpuUtilization").toString()));
                    sse.setDiskTotal(Long.parseLong(m.get("totalDiskSpace").toString()));
                    sse.setDiskFree(Long.parseLong(m.get("freeDiskSpace").toString()));
                    sse.setSwapFree(Long.parseLong(m.get("SwapFree").toString()));
                    sse.setSwapTotal(Long.parseLong(m.get("SwapTotal").toString()));
                    sse.setActiveHosts(UvmContextFactory.context().hostTable().getCurrentActiveSize());
                    logger.debug("Logging SystemStatEvent");
                    UvmContextFactory.context().logEvent(sse);

                    lastLogTimeStamp = time;
                }
                
                for( InterfaceSettings intfSettings : UvmContextFactory.context().networkManager().getNetworkSettings().getInterfaces() ) {
                    // do not log stats for disabled interfaces
                    if ( intfSettings.getConfigType() == InterfaceSettings.ConfigType.DISABLED )
                        continue;
                    String key = "interface_" + intfSettings.getInterfaceId() + "_";
                    Object rxBps_o = m.get(key + "rxBps");
                    Object txBps_o = m.get(key + "txBps");
                    if ( rxBps_o == null || txBps_o == null )
                        continue;
                    double rxBps = Double.parseDouble( rxBps_o.toString() );
                    double txBps = Double.parseDouble( txBps_o.toString() );
                    InterfaceStatEvent event = new InterfaceStatEvent();
                    event.setInterfaceId( intfSettings.getInterfaceId() );
                    event.setRxRate( rxBps );
                    event.setTxRate( txBps );
                    UvmContextFactory.context().logEvent(event);
                }
            } catch (Exception e) {
                logger.warn("Exception:",e);
            }

            systemStats = Collections.unmodifiableMap(m);
        }

        private void readMeminfo(Map<String, Object> m) throws IOException
        {

            readProcFile("/proc/meminfo", MEMINFO_PATTERN, MEMINFO_KEEPERS, m, 1024);

            Long memFree = (Long)m.get("MemFree");
            if (null == memFree) {
                memFree = 0L;
            }

            Long i = (Long)m.get("Cached");
            if ( i != null ) {
                memFree += i;
            }

            i = (Long)m.get("Buffers");
            if ( i != null ) {
                memFree += i;
            }

            m.put("MemFree", memFree);
        }

        private void readVmstat(Map<String, Object> m) throws IOException
        {
            readProcFile("/proc/vmstat", VMSTAT_PATTERN, VMSTAT_KEEPERS, m, 1);
        }

        private void readProcFile(String filename, Pattern p, Set<String> keepers, Map<String, Object> m, int multiple) throws IOException
        {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(filename));
                for (String l = br.readLine(); null != l; l = br.readLine()) {
                    Matcher matcher = p.matcher(l);
                    if (matcher.find()) {
                        String n = matcher.group(1);
                        if (keepers.contains(n)) {
                            String s = matcher.group(2);
                            try {
                                m.put(n, Long.parseLong(s) * multiple);
                            } catch (NumberFormatException exn) {
                                logger.warn("could not add value for: " + n);
                            }
                        }
                    }
                }
            } finally {
                if (null != br) {
                    br.close();
                }
            }
        }

        private void readCpuinfo(Map<String, Object> m) throws IOException
        {
            String cpuModel = null;
            double cpuSpeed = 0;
            int numCpus = 0;

            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("/proc/cpuinfo"));
                for (String l = br.readLine(); null != l; l = br.readLine()) {
                    Matcher matcher = CPUINFO_PATTERN.matcher(l);
                    if (matcher.find()) {
                        String n = matcher.group(1);
                        if (n.equals("model name") || n.equals("Processor")) {
                            cpuModel = matcher.group(2);
                        } else if (n.equals("processor")) {
                            numCpus++;
                        } else if (n.equals("cpu MHz")) {
                            String v = matcher.group(2);
                            try {
                                cpuSpeed = Double.parseDouble(v);
                            } catch (NumberFormatException exn) {
                                logger.warn("could not parse cpu speed: " + v);
                            }
                        }
                    }
                }
            } finally {
                if (null != br) {
                    br.close();
                }
            }

            m.put("cpuModel", cpuModel);
            m.put("cpuSpeed", cpuSpeed);
            m.put("numCpus", numCpus);
        }

        private void readArchinfo(Map<String, Object> m) throws IOException
        {
            m.put("architecture", System.getProperty("os.arch", "unknown"));
        }

        private void readUptime(Map<String, Object> m) throws IOException
        {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("/proc/uptime"));

                String l = br.readLine();
                if (null != l) {
                    String s = l.split(" ")[0];
                    try {
                        m.put("uptime", Double.parseDouble(s));
                    } catch (NumberFormatException exn) {
                        logger.warn("could not parse uptime: " + s);
                    }
                }
            } finally {
                if (null != br) {
                    br.close();
                }
            }
        }

        private void readLoadAverage(Map<String, Object> m) throws IOException
        {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("/proc/loadavg"));
                String l = br.readLine();
                if (null != l) {
                    String[] s = l.split(" ");
                    if (s.length >= 3) {
                        try {
                            m.put("oneMinuteLoadAvg", Double.parseDouble(s[0]));
                            m.put("fiveMinuteLoadAvg", Double.parseDouble(s[1]));
                            m.put("fifteenMinuteLoadAvg", Double.parseDouble(s[2]));
                        } catch (NumberFormatException exn) {
                            logger.warn("could not parse loadavg: " + l);
                        }
                    }
                }
            } finally {
                if (null != br) {
                    br.close();
                }
            }
        }

        private void getNumProcs(Map<String, Object> m)
        {
            int numProcs = 0;

            File dir = new File("/proc");
            if (dir.isDirectory()) {
                for (File f : dir.listFiles()) {
                    try {
                        Integer.parseInt(f.getName());
                        numProcs++;
                    } catch (NumberFormatException exn) { }
                }
            }

            m.put("numProcs", numProcs);
        }

        private void getCpuUsage(Map<String, Object> m) throws IOException
        {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("/proc/stat"));
                for (String l = br.readLine(); null != l; l = br.readLine()) {
                    Matcher matcher = CPU_USAGE_PATTERN.matcher(l);
                    if (matcher.find()) {
                        try {
                            long user1 = Long.parseLong(matcher.group(1));
                            user1 = incrementCount(user0, user1);
                            long nice1 = Long.parseLong(matcher.group(2));
                            nice1 = incrementCount(nice0, nice1);
                            long system1 = Long.parseLong(matcher.group(3));
                            system1 = incrementCount(system0, system1);
                            long idle1 = Long.parseLong(matcher.group(4));
                            idle1 = incrementCount(idle0, idle1);

                            long totalTime = (user1 - user0) + (nice1 - nice0) + (system1 - system0) + (idle1 - idle0);

                            if (0 == totalTime) {
                                m.put("userCpuUtilization", (double)0);
                                m.put("systemCpuUtilization", (double)0);
                            } else {
                                m.put("userCpuUtilization", ((user1 - user0) + (nice1 - nice0)) / (double)totalTime);
                                m.put("systemCpuUtilization", (system1 - system0) / (double)totalTime);
                            }

                            user0 = user1;
                            nice0 = nice1;
                            system0 = system1;
                            idle0 = idle1;
                        } catch (NumberFormatException exn) {
                            m.put("userCpuUtilization", 0.0);
                            m.put("systemCpuUtilization", 0.0);
                        }

                        break;
                    }
                }
            } finally {
                if (null != br) {
                    br.close();
                }
            }
        }

        private synchronized void getNetDevUsage(Map<String, Object> m) throws IOException
        {
            long totalRxBytesOldValue = 0, totalRxBytesNewValue = 0;
            long totalTxBytesOldValue = 0, totalTxBytesNewValue = 0;
            long wansRxBytesOldValue = 0, wansRxBytesNewValue = 0;
            long wansTxBytesOldValue = 0, wansTxBytesNewValue = 0;

            NetcapManager nm = UvmContextImpl.getInstance().netcapManager();

            m.put( "uvmSessions", nm.getSessionCount() );
            m.put( "uvmTCPSessions", nm.getSessionCount(SessionTuple.PROTO_TCP) );
            m.put( "uvmUDPSessions", nm.getSessionCount(SessionTuple.PROTO_UDP) );
            m.put( "activeHosts", UvmContextFactory.context().hostTable().getCurrentActiveSize() );
            m.put( "maxActiveHosts", UvmContextFactory.context().hostTable().getMaxActiveSize() );
            m.put( "knownDevices", UvmContextFactory.context().deviceTable().size() );
            
            long currentTime = System.currentTimeMillis();

            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("/proc/net/dev"));
                Integer i = new Integer(0);

                for (String l = br.readLine(); null != l; l = br.readLine(), i += 1) {
                    Matcher matcher = NET_DEV_PATTERN.matcher(l);
                    if (matcher.find()) {
                        String iface = matcher.group(1);
                        
                        InterfaceSettings intfSettings = UvmContextFactory.context().networkManager().findInterfaceSystemDev( iface );
                        // Restrict to only the WAN interfaces (bug 5616)
                        if ( intfSettings == null )
                            continue;
                        int intfId = intfSettings.getInterfaceId();

                        // get stored previous values or initialize them to 0
                        Long rxBytesOld = rxtxBytesStore.get("rx"+intfId);
                        if (rxBytesOld == null) rxBytesOld = 0L;
                        Long txBytesOld = rxtxBytesStore.get("tx"+intfId);
                        if (txBytesOld == null) txBytesOld = 0L;

                        try {
                            // parse new incoming values w/64-bit correction for 32-bit rollover
                            long rxBytesNew = Long.parseLong(matcher.group(2));
                            long txBytesNew = Long.parseLong(matcher.group(3));
                            if ( rxBytesNew < rxBytesOld ) {
                                // either an overflow has happened or the interfaces have been reset
                                // unfortunately we can't tell which
                                rxBytesOld = 0L;
                            }
                            if ( txBytesNew < txBytesOld ) {
                                // either an overflow has happened or the interfaces have been reset
                                // unfortunately we can't tell which
                                txBytesOld = 0L;
                            }

                            // accumulate old values
                            totalRxBytesOldValue += rxBytesOld;
                            totalTxBytesOldValue += txBytesOld;
                            if (intfSettings.getIsWan()) {
                                wansRxBytesOldValue += rxBytesOld;
                                wansTxBytesOldValue += txBytesOld;
                            }
                            
                            // accumulate new values
                            totalRxBytesNewValue += rxBytesNew;
                            totalTxBytesNewValue += txBytesNew;
                            if (intfSettings.getIsWan()) {
                                wansRxBytesNewValue += rxBytesNew;
                                wansTxBytesNewValue += txBytesNew;
                            }

                            // update stored old values
                            rxtxBytesStore.put("rx"+intfId, rxBytesNew);
                            rxtxBytesStore.put("tx"+intfId, txBytesNew);

                            // store the new values
                            String key = "interface_" + intfSettings.getInterfaceId() + "_";
                            double dt = (currentTime - lastNetDevUpdate) / 1000.0;
                            if (Math.abs(dt) < 5.0e-5) {
                                m.put(key + "rxBps", 0.0);
                                m.put(key + "txBps", 0.0);
                            } else {
                                double rd = (( rxBytesNew - rxBytesOld ) / dt );
                                double td = (( txBytesNew - txBytesOld ) / dt );
                                if ( rd > 100000000 ) {
                                    logger.warn("Suspicious rxBytes value: " + rd);
                                    logger.warn("New bytes          value: " + rxBytesNew);
                                    logger.warn("Old bytes          value: " + rxBytesOld);
                                    logger.warn("Diff bytes         value: " + (rxBytesNew - rxBytesOld));
                                    logger.warn("Diff time          value: " + dt);
                                }
                                m.put(key + "rxBps", rd);
                                m.put(key + "txBps", td);
                            }
                        } catch (NumberFormatException exn) {
                            logger.warn("could not add interface info for: " + iface, exn);
                        }
                    }
                }
            } finally {
                if (null != br) {
                    br.close();
                }
            }

            double dt = (currentTime - lastNetDevUpdate) / 1000.0;
            if (Math.abs(dt) < 5.0e-5) {
                m.put("interface_wans_rxBps", 0.0);
                m.put("interface_wans_txBps", 0.0);
            } else {
                m.put("interface_wans_rxBps", (wansRxBytesNewValue - wansRxBytesOldValue) / dt);
                m.put("interface_wans_txBps", (wansTxBytesNewValue - wansTxBytesOldValue) / dt);
            }
            if (Math.abs(dt) < 5.0e-5) {
                m.put("interface_total_rxBps", 0.0);
                m.put("interface_total_txBps", 0.0);
            } else {
                m.put("interface_total_rxBps", (totalRxBytesNewValue - totalRxBytesOldValue) / dt);
                m.put("interface_total_txBps", (totalTxBytesNewValue - totalTxBytesOldValue) / dt);
            }
            lastNetDevUpdate = currentTime;
        }

        private synchronized void getDiskUsage(Map<String, Object> m) throws IOException
        {
            File root = new File("/");
            m.put("totalDiskSpace", root.getTotalSpace());
            m.put("freeDiskSpace", root.getFreeSpace());

            long diskReads0 = 0, diskReads1 = 0;
            long diskWrites0 = 0, diskWrites1 = 0;

            long currentTime = System.currentTimeMillis();

            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("/proc/diskstats"));
                Integer i = new Integer(0);
                for (String l = br.readLine(); null != l; l = br.readLine(), i += 1) {
                    Matcher matcher = DISK_STATS_PATTERN.matcher(l);
                    if (matcher.find()) {
                        Long diskreads0 = diskRW0.get("dr"+i);
                        if (diskreads0 == null) diskreads0 = 0L;
                        Long diskwrites0 = diskRW0.get("dw"+i);
                        if (diskwrites0 == null) diskwrites0 = 0L;

                        try {
                            // accumulate previous values
                            diskReads0 += diskreads0;
                            diskWrites0 += diskwrites0;
                            // parse new incoming values w/64-bit correction for 32-bit rollover
                            long diskreads1 = incrementCount(diskreads0.longValue(), Long.parseLong(matcher.group(1)));
                            long diskwrites1 = incrementCount(diskwrites0.longValue(), Long.parseLong(matcher.group(2)));
                            // accumulate 64-bit corrected values
                            diskReads1 += diskreads1;
                            diskWrites1 += diskwrites1;
                            // update stored previous values w/new 64 corrected values
                            diskRW0.put("dr"+i, diskreads1);
                            diskRW0.put("dw"+i, diskwrites1);
                        } catch (NumberFormatException exn) {
                            logger.warn("could not get disk data", exn);
                        }
                    }
                }
            } finally {
                if (null != br) {
                    br.close();
                }
            }

            m.put("diskReads", diskReads1);
            m.put("diskWrites", diskWrites1);

            double dt = (currentTime - lastDiskUpdate) / 1000.0;
            if (Math.abs(dt) < 5.0e-5) {
                m.put("diskReadsPerSecond", 0.0);
                m.put("diskWritesPerSecond", 0.0);
            } else {
                m.put("diskReadsPerSecond", (diskReads1 - diskReads0) / dt);
                m.put("diskWritesPerSecond", (diskWrites1 - diskWrites0) / dt);
            }
            lastDiskUpdate = currentTime;
        }

        /**
         * This takes the previous number and the current number
         * If the current number is less than the previous number, then we can assume it has wrapped
         * This function ORs the higher bit values with the new value so we dont lose the high value bit value
         */
        private long incrementCount(long previousCount, long kernelCount)
        {
            /* If the kernel is counting in 64-bits, just return the
             * kernel count */
            if (kernelCount >= (1L << 32)) return kernelCount;

            long previousKernelCount = previousCount & 0xFFFFFFFFL;
            if (previousKernelCount > kernelCount) previousCount += (1L << 32);

            return ((previousCount & 0x7FFFFFFF00000000L) + kernelCount);
        }
    }

    static {
        Set<String> s = new HashSet<String>();
        s.add("MemTotal");
        s.add("MemFree");
        s.add("Cached");
        s.add("Buffers");
        s.add("Active");
        s.add("Inactive");
        s.add("SwapTotal");
        s.add("SwapFree");
        MEMINFO_KEEPERS = Collections.unmodifiableSet(s);

        s = new HashSet<String>();
        s.add("pgpgin");
        s.add("pgpgout");
        s.add("pgfault");
        VMSTAT_KEEPERS = Collections.unmodifiableSet(s);
    }
}
