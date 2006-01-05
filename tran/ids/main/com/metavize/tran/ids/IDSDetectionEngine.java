package com.metavize.tran.ids;

import java.nio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import com.metavize.mvvm.argon.SessionEndpoints;
import com.metavize.mvvm.tapi.*;
import com.metavize.mvvm.tapi.event.*;
import com.metavize.mvvm.tran.firewall.IPMatcher;
import com.metavize.mvvm.tran.ParseException;
import com.metavize.mvvm.tran.PortRange;
import com.metavize.mvvm.tran.Transform;

public class IDSDetectionEngine {

    // Any chunk that takes this long gets an error
    public static final long ERROR_ELAPSED = 200;
    // Any chunk that takes this long gets a warning
    public static final long WARN_ELAPSED = 20;

    private static final int SCAN_COUNTER  = Transform.GENERIC_0_COUNTER;

    private int 	        maxChunks 	= 8;
    private IDSSettings 	settings 	= null;
    private Map<String,RuleClassification> classifications = null;

    private IDSRuleManager 	rules;
    private IDSTransformImpl 	transform;

    // We can't just attach the session info to a session, we have to attach it to the 'pipeline', since
    // we have to access it from multiple pipes (octet & http).  So we keep the registry here.
    private Map<Integer, IDSSessionInfo> sessionInfoMap = new ConcurrentHashMap<Integer, IDSSessionInfo>();

    Map<Integer,List<IDSRuleHeader>> 	portS2CMap      = new ConcurrentHashMap<Integer,List<IDSRuleHeader>>();
    Map<Integer,List<IDSRuleHeader>> 	portC2SMap 	= new ConcurrentHashMap<Integer,List<IDSRuleHeader>>();
	
    private static final Logger log = Logger.getLogger(IDSDetectionEngine.class);
	
    /*private static IDSDetectionEngine instance = new IDSDetectionEngine();
      public  static IDSDetectionEngine instance() {
      if(instance == null) 
      instance = new IDSDetectionEngine();
      return instance;
      }*/

    public IDSDetectionEngine(IDSTransformImpl transform) {
        this.transform = transform;
        rules = new IDSRuleManager(this);
        //The Goggles! They do nothing!
        /*String test = "alert tcp 10.0.0.40-10.0.0.101 any -> 66.35.250.0/24 80 (content:\"slashdot\"; msg:\"OMG teH SLASHd0t\";)";
          String tesT = "alert tcp 10.0.0.1/24 any -> any any (content: \"spOOns|FF FF FF FF|spoons\"; msg:\"Matched binary FF FF FF and spoons\"; nocase;)";
          String TesT = "alert tcp 10.0.0.1/24 any -> any any (uricontent:\"slashdot\"; nocase; msg:\"Uricontent matched\";)";
          try {
          rules.addRule(test);
          rules.addRule(tesT);
          rules.addRule(TesT);
          } catch (ParseException e) {
          log.warn("Could not parse rule; " + e.getMessage());
          }*/
    }

    public RuleClassification getClassification(String classificationName) {
        return classifications.get(classificationName);
    }

    public void setClassifications(List<RuleClassification> classificationList) {
        classifications = new HashMap<String, RuleClassification>();
        for (RuleClassification rc : classificationList)
            classifications.put(rc.getName(), rc);
    }

    public IDSSettings getSettings() {
        return settings;
    }

    public void setSettings(IDSSettings settings) {
        this.settings = settings;
    }
	
    //fix this - settigns?
    public void setMaxChunks(int max) {
        maxChunks = max;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public void updateUICount(int counter) {
        transform.incrementCount(counter);
    }

    public void onReconfigure() {
        portC2SMap = new ConcurrentHashMap<Integer,List<IDSRuleHeader>>();
        portS2CMap = new ConcurrentHashMap<Integer,List<IDSRuleHeader>>();
		
        rules.onReconfigure();
        log.debug("Done with reconfigure");
    }

    public void stop() {
        portC2SMap = new ConcurrentHashMap<Integer,List<IDSRuleHeader>>();
        portS2CMap = new ConcurrentHashMap<Integer,List<IDSRuleHeader>>();
        sessionInfoMap = new ConcurrentHashMap<Integer, IDSSessionInfo>();
    }

    public void updateRule(IDSRule rule) {
        try {
            rules.updateRule(rule);
        } catch (ParseException e) {
            log.warn("Could not parse rule: ", e);
        } catch (Exception e) {
            log.error("Some sort of really bad exception: ", e);
            log.error("For rule: " + rule);
        }	
    }
	
    //Deprecating?
    public boolean addRule(IDSRule rule) {
        try {
            return (rules.addRule(rule));
        } catch (ParseException e) { 
            log.warn("Could not parse rule: ", e); 
        } catch (Exception e) {
            log.error("Some sort of really bad exception: ", e);
            log.error("For rule: " + rule);
        }
        return false;
    }

    public void processNewSessionRequest(IPNewSessionRequest request, Protocol protocol) {
		
        //Get Mapped list
        List<IDSRuleHeader> c2sList = portC2SMap.get(request.serverPort());
        List<IDSRuleHeader> s2cList = portS2CMap.get(request.serverPort());
		
        if(c2sList == null) {
            c2sList = rules.matchingPortsList(request.serverPort(), IDSRuleManager.TO_SERVER);
            portC2SMap.put(request.serverPort(),c2sList);

            if (log.isDebugEnabled())
                log.debug("c2sHeader list Size: "+c2sList.size() + " For port: "+request.serverPort());
        }
		
        if(s2cList == null) {
            s2cList = rules.matchingPortsList(request.serverPort(), IDSRuleManager.TO_CLIENT);
            portS2CMap.put(request.serverPort(),s2cList);
			
            if (log.isDebugEnabled())
                log.debug("s2cHeader list Size: "+s2cList.size() + " For port: "+request.serverPort());
        }
		
        //Check matches
        List<IDSRuleSignature> c2sSignatures = rules.matchesHeader(request, request.isInbound(), IDSRuleManager.TO_SERVER, c2sList);

        List<IDSRuleSignature> s2cSignatures = rules.matchesHeader(request, request.isInbound(), IDSRuleManager.TO_CLIENT, s2cList);
			
        if (log.isDebugEnabled())
            log.debug("s2cSignature list size: " + s2cSignatures.size() + ", c2sSignature list size: " +
                      c2sSignatures.size());
        if(c2sSignatures.size() > 0 || s2cSignatures.size() > 0) {
            request.attach(new Object[] { c2sSignatures, s2cSignatures });
        } else {
            request.release();
        }
    }

    public IDSSessionInfo getSessionInfo(IPSession session) {
        return sessionInfoMap.get(session.id());
    }

    public void processNewSession(IPSession session, Protocol protocol) {
        Object[] sigs = (Object[]) session.attachment();
        List<IDSRuleSignature> c2sSignatures = (List<IDSRuleSignature>) sigs[0];
        List<IDSRuleSignature> s2cSignatures = (List<IDSRuleSignature>) sigs[1];

        log.debug("registering IDSSessionInfo");
        IDSSessionInfo info = new IDSSessionInfo(session);
        info.setC2SSignatures(c2sSignatures);
        info.setS2CSignatures(s2cSignatures);
        sessionInfoMap.put(session.id(), info);
        session.attach(null);
    }

    public void processFinalized(IPSession session, Protocol protocol) {
        log.debug("unregistering IDSSessionInfo");
        sessionInfoMap.remove(session.id());
    }

    public IDSRuleManager getRulesForTesting() {
        return rules;
    }
	
    public void dumpRules()
    {
        rules.dumpRules();
    }

    //In process of fixing this
    public void handleChunk(IPDataEvent event, IPSession session, boolean isFromServer) {
        try {
            long startTime = System.currentTimeMillis();
		
            SessionStats stats = session.stats();
            if(stats.s2tChunks() >= maxChunks || stats.c2tChunks() >= maxChunks)
                // Takes effect after this packet/chunk
                session.release();
		
            IDSSessionInfo info = sessionInfoMap.get(session.id());
		
            info.setEvent(event);
            info.setFlow(isFromServer);
		
            updateUICount(SCAN_COUNTER);

            boolean result;
            if(isFromServer)
                result = info.processS2CSignatures();
            else
                result = info.processC2SSignatures();

            if (!result)
                transform.statisticManager.incrDNC();

            long elapsed = System.currentTimeMillis() - startTime;
            if (isFromServer) {
                int numsigs = info.numS2CSignatures();
                if (elapsed > ERROR_ELAPSED)
                    log.error("took " + elapsed + "ms to run " + numsigs + " s2c rules");
                else if (elapsed > WARN_ELAPSED)
                    log.warn("took " + elapsed + "ms to run " + numsigs + " s2c rules");
                else if (log.isDebugEnabled())
                    log.debug("ms to run " + numsigs + " s2c rules: " + elapsed);
            } else {
                int numsigs = info.numC2SSignatures();
                if (elapsed > ERROR_ELAPSED)
                    log.error("took " + elapsed + "ms to run " + numsigs + " c2s rules");
                else if (elapsed > WARN_ELAPSED)
                    log.warn("took " + elapsed + "ms to run " + numsigs + " c2s rules");
                else if (log.isDebugEnabled())
                    log.debug("ms to run " + numsigs + " c2s rules: " + elapsed);
            }
        } catch (Exception e) {
            log.error("Error parsing chunk: ", e);
        }
    }
}
