/*
 * $HeadURL$
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.node.spyware;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.sleepycat.je.DatabaseException;
import com.untangle.node.http.UserWhitelistMode;
import com.untangle.node.token.Header;
import com.untangle.node.token.Token;
import com.untangle.node.token.TokenAdaptor;
import com.untangle.node.util.PrefixUrlList;
import com.untangle.node.util.UrlDatabase;
import com.untangle.node.util.UrlDatabaseResult;
import com.untangle.node.util.UrlList;
import com.untangle.uvm.LocalAppServerManager;
import com.untangle.uvm.LocalUvmContext;
import com.untangle.uvm.LocalUvmContextFactory;
import com.untangle.uvm.logging.EventLogger;
import com.untangle.uvm.logging.EventLoggerFactory;
import com.untangle.uvm.logging.EventManager;
import com.untangle.uvm.logging.SimpleEventFilter;
import com.untangle.uvm.node.IPMaddr;
import com.untangle.uvm.node.IPMaddrRule;
import com.untangle.uvm.node.NodeContext;
import com.untangle.uvm.node.StringRule;
import com.untangle.uvm.toolbox.RemoteToolboxManager;
import com.untangle.uvm.util.OutsideValve;
import com.untangle.uvm.util.TransactionWork;
import com.untangle.uvm.vnet.AbstractNode;
import com.untangle.uvm.vnet.Affinity;
import com.untangle.uvm.vnet.Fitting;
import com.untangle.uvm.vnet.PipeSpec;
import com.untangle.uvm.vnet.SoloPipeSpec;
import com.untangle.uvm.vnet.TCPSession;
import org.apache.catalina.Valve;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

public class SpywareImpl extends AbstractNode implements Spyware
{
    private static final String ACTIVEX_LIST
        = "com/untangle/node/spyware/activex.txt";
    private static final String ACTIVEX_DIFF_BASE
        = "com/untangle/node/spyware/activex-diff-";
    private static final String COOKIE_LIST
        = "com/untangle/node/spyware/cookie.txt";
    private static final String COOKIE_DIFF_BASE
        = "com/untangle/node/spyware/cookie-diff-";
    private static final String SUBNET_LIST
        = "com/untangle/node/spyware/subnet.txt";
    private static final String SUBNET_DIFF_BASE
        = "com/untangle/node/spyware/subnet-diff-";

    private static final File DB_HOME = new File(System.getProperty("bunnicula.db.dir"), "spyware");
    private static final URL BLACKLIST_HOME;

    private static final int HTTP = 0;
    private static final int BYTE = 1;

    private static int deployCount = 0;

    static {
        try {
            BLACKLIST_HOME = new URL("http://webupdates.untangle.com/diffserver");
        } catch (MalformedURLException exn) {
            throw new RuntimeException(exn);
        }
    }

    private final SpywareHttpFactory factory = new SpywareHttpFactory(this);
    private final TokenAdaptor tokenAdaptor = new TokenAdaptor(this, factory);
    private final SpywareEventHandler streamHandler = new SpywareEventHandler(this);

    private final EventLogger<SpywareEvent> eventLogger;

    private final UrlDatabase urlDatabase = new UrlDatabase();
    private final UrlDatabase cookieDatabase = new UrlDatabase();

    private final PipeSpec[] pipeSpecs = new PipeSpec[]
        { new SoloPipeSpec("spyware-http", this, tokenAdaptor,
                           Fitting.HTTP_TOKENS, Affinity.SERVER, 0),
          new SoloPipeSpec("spyware-byte", this, streamHandler,
                           Fitting.OCTET_STREAM, Affinity.SERVER, 0) };

    private final Map<InetAddress, Set<String>> hostWhitelists
        = new HashMap<InetAddress, Set<String>>();

    private final Logger logger = Logger.getLogger(getClass());

    private volatile SpywareSettings settings;

    private volatile Map<String, StringRule> activeXRules;
    private volatile Map<String, StringRule> cookieRules;
    private volatile Set<String> domainWhitelist;

    private final SpywareReplacementGenerator replacementGenerator;

    final SpywareStatisticManager statisticManager;

    // constructors -----------------------------------------------------------

    public SpywareImpl()
    {
        replacementGenerator = new SpywareReplacementGenerator(getTid());

        LocalUvmContext uvm = LocalUvmContextFactory.context();
        Map m = new HashMap();
        m.put("key", uvm.getActivationKey());
        RemoteToolboxManager tm = uvm.toolboxManager();
        Boolean rup = tm.hasPremiumSubscription();
        m.put("premium", rup.toString());
        m.put("client-version", uvm.getFullVersion());

        for (String list : getSpywareLists()) {
            if (list.startsWith("spyware-")) {
                UrlDatabase db = list.endsWith("cookie") ? cookieDatabase : urlDatabase;
                try {
                    UrlList l = new PrefixUrlList(DB_HOME, BLACKLIST_HOME, list, m);
                    db.addBlacklist(list, l);
                    db.updateAll(true);
                } catch (IOException exn) {
                    logger.warn("could not set up database", exn);
                } catch (DatabaseException exn) {
                    logger.warn("could not set up database", exn);
                }
            }
        }

        NodeContext tctx = getNodeContext();
        eventLogger = EventLoggerFactory.factory().getEventLogger(tctx);
        statisticManager = new SpywareStatisticManager(tctx);

        SimpleEventFilter ef = new SpywareAllFilter();
        eventLogger.addSimpleEventFilter(ef);
        ef = new SpywareBlockedFilter();
        eventLogger.addSimpleEventFilter(ef);
        ef = new SpywareAccessFilter();
        eventLogger.addSimpleEventFilter(ef);
        ef = new SpywareActiveXFilter();
        eventLogger.addSimpleEventFilter(ef);
        ef = new SpywareBlacklistFilter();
        eventLogger.addSimpleEventFilter(ef);
        ef = new SpywareCookieFilter();
        eventLogger.addSimpleEventFilter(ef);
    }

    // SpywareNode methods -----------------------------------------------

    public SpywareSettings getSpywareSettings()
    {
        if( settings == null )
            logger.error("Settings not yet initialized. State: " + getNodeContext().getRunState() );
        return settings;
    }

    public void setSpywareSettings(final SpywareSettings settings)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    s.merge(settings);
                    SpywareImpl.this.settings = settings;
                    return true;
                }

                public Object getResult() { return null; }
            };
        getNodeContext().runTransaction(tw);

        reconfigure();
    }

    public UserWhitelistMode getUserWhitelistMode()
    {
        return settings.getUserWhitelistMode();
    }

    public SpywareBlockDetails getBlockDetails(String nonce)
    {
        return replacementGenerator.getNonceData(nonce);
    }

    public boolean unblockSite(String nonce, boolean global)
    {
        SpywareBlockDetails bd = replacementGenerator.removeNonce(nonce);

        switch (settings.getUserWhitelistMode()) {
        case NONE:
            logger.debug("attempting to unblock in UserWhitelistMode.NONE");
            return false;
        case USER_ONLY:
            if (global) {
                logger.debug("attempting to unblock global in UserWhitelistMode.USER_ONLY");
                return false;
            }
        case USER_AND_GLOBAL:
            // its all good
            break;
        default:
            logger.error("missing case: " + settings.getUserWhitelistMode());
            break;
        }

        if (null == bd) {
            logger.debug("no BlockDetails for nonce");
            return false;
        } else if (global) {
            String site = bd.getWhitelistHost();
            if (null == site) {
                logger.warn("cannot unblock null host");
                return false;
            } else {
                logger.warn("permanently unblocking site: " + site);
                StringRule sr = new StringRule(site, site, "user whitelisted",
                                               "whitelisted by user", true);
                settings.getDomainWhitelist().add(sr);
                setSpywareSettings(settings);

                return true;
            }
        } else {
            String site = bd.getWhitelistHost();
            if (null == site) {
                logger.warn("cannot unblock null host");
                return false;
            } else {
                logger.warn("temporarily unblocking site: " + site);
                InetAddress addr = bd.getClientAddress();

                synchronized (this) {
                    Set<String> wl = hostWhitelists.get(addr);
                    if (null == wl) {
                        wl = new HashSet<String>();
                        hostWhitelists.put(addr, wl);
                    }
                    wl.add(site);
                }

                return true;
            }
        }
    }

    public EventManager<SpywareEvent> getEventManager()
    {
        return eventLogger;
    }

    // Node methods ------------------------------------------------------

    // AbstractNode methods ----------------------------------------------

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    public void initializeSettings()
    {
        SpywareSettings settings = new SpywareSettings(getTid());

        updateActiveX(settings);
        updateCookie(settings);
        updateSubnet(settings);

        setSpywareSettings(settings);

        statisticManager.stop();
    }

    protected void postInit(String[] args)
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    Query q = s.createQuery
                        ("from SpywareSettings ss where ss.tid = :tid");
                    q.setParameter("tid", getTid());
                    SpywareImpl.this.settings = (SpywareSettings)q.uniqueResult();

                    updateActiveX(SpywareImpl.this.settings);
                    updateCookie(SpywareImpl.this.settings);
                    updateSubnet(SpywareImpl.this.settings);

                    return true;
                }
            };
        getNodeContext().runTransaction(tw);

        reconfigure();

        deployWebAppIfRequired(logger);
    }

    @Override
    protected void preStart()
    {
        statisticManager.start();
        urlDatabase.startUpdateTimer();
        cookieDatabase.startUpdateTimer();
    }

    @Override
    protected void postStop()
    {
        statisticManager.stop();
        urlDatabase.stopUpdateTimer();
        cookieDatabase.stopUpdateTimer();
    }

    @Override
    protected void postDestroy()
    {
        unDeployWebAppIfRequired(logger);
    }

    // package private methods ------------------------------------------------

    Token[] generateResponse(SpywareBlockDetails bd, TCPSession sess,
                             String uri, Header header, boolean persistent)
    {
        String n = replacementGenerator.generateNonce(bd);
        return replacementGenerator.generateResponse(n, sess, uri, header,
                                                     persistent);
    }

    String generateNonce(String host, String uri, InetAddress addr)
    {
        SpywareBlockDetails bd = new SpywareBlockDetails(host, uri, addr);

        return replacementGenerator.generateNonce(bd);
    }

    boolean isBlacklistDomain(String domain, URI uri)
    {
        if (!settings.getUrlBlacklistEnabled()) {
            return false;
        }

        boolean match = false;

        domain = null == domain ? null : domain.toLowerCase();
        for (String d = domain; !match && null != d; d = nextHost(d)) {
            UrlDatabaseResult udr = urlDatabase.search("http", d, "/");
            match = null != udr && udr.blacklisted();
        }

        return match;
    }

    boolean isWhitelistedDomain(String domain, InetAddress clientAddr)
    {
        if (null == domain) {
            return false;
        } else {
            domain = domain.toLowerCase();

            if (findMatch(domainWhitelist, domain)) {
                return true;
            } else {
                Set<String> l = hostWhitelists.get(clientAddr);
                if (null == l) {
                    return false;
                } else {
                    return findMatch(l, domain);
                }
            }
        }
    }

    boolean isBlockedCookie(String domain)
    {
        if (null == domain) {
            logger.warn("null domain for cookie");
            return false;
        }

        domain = domain.startsWith(".") && 1 < domain.length()
            ? domain.substring(1) : domain;

        if (null == cookieRules && !settings.getCookieBlockerEnabled()) {
            return false;
        }

        UrlDatabaseResult udr = cookieDatabase.search("http", domain, "/");
        boolean match = null != udr && udr.blacklisted();

        for (String d = domain; !match && null != d; d = nextHost(d)) {
            StringRule sr = cookieRules.get(d);
            match = null != sr && sr.isLive();
        }

        return match;
    }

    StringRule getBlockedActiveX(String clsId)
    {
        return null == activeXRules ? null : activeXRules.get(clsId);
    }

    void log(SpywareEvent se)
    {
        eventLogger.log(se);
    }

    // private methods --------------------------------------------------------

    private boolean findMatch(Set<String> rules, String domain)
    {
        for (String d = domain; null != d; d = nextHost(d)) {
            if (rules.contains(d)) {
                return true;
            }
        }

        return false;
    }

    // XXX factor this shit out!
    private String nextHost(String host)
    {
        int i = host.indexOf('.');
        if (0 > i || i == host.lastIndexOf('.')) {
            return null;  /* skip TLD */
        }

        return host.substring(i + 1);
    }

    // settings intialization -------------------------------------------------

    private void updateActiveX(SpywareSettings settings)
    {
        int ver = settings.getActiveXVersion();

        if (0 > ver || null == settings.getActiveXRules()) {
            List l = settings.getActiveXRules();
            if (null != l) {
                l.clear();
            }
            Set<String> add = initList(ACTIVEX_LIST);
            Set<String> remove = Collections.emptySet();
            updateActiveX(settings, add, remove);
            settings.setActiveXVersion(latestVer(ACTIVEX_DIFF_BASE));
        } else {
            Set<String> add = new HashSet<String>();
            Set<String> remove = new HashSet<String>();
            ver = diffSets(ACTIVEX_DIFF_BASE, ver, add, remove);
            updateActiveX(settings, add, remove);
            settings.setActiveXVersion(ver);
        }
    }

    private void updateActiveX(SpywareSettings settings, Set<String> add,
                               Set<String> remove)
    {
        List<StringRule> rules = (List<StringRule>)settings.getActiveXRules();
        if (null == rules) {
            rules = new LinkedList<StringRule>();
            settings.setActiveXRules(rules);
        }

        for (Iterator<StringRule> i = rules.iterator(); i.hasNext(); ) {
            StringRule sr = i.next();
            if (remove.contains(sr.getString())) {
                i.remove();
                if (logger.isDebugEnabled()) {
                    logger.debug("removing activex: " + sr.getString());
                }
            } else {
                remove.remove(sr);
                if (logger.isDebugEnabled()) {
                    logger.debug("not removing activex: " + sr.getString());
                }
            }
        }

        for (String s : add) {
            logger.debug("adding activex: " + s);
            rules.add(new StringRule(s));
        }
    }

    private void updateCookie(SpywareSettings settings)
    {
        int ver = settings.getCookieVersion();

        if (0 > ver || null == settings.getCookieRules()) {
            List l = settings.getCookieRules();
            if (null != l) {
                l.clear();
            }
            Set<String> add = initList(COOKIE_LIST);
            Set<String> remove = Collections.emptySet();
            updateCookie(settings, add, remove);
            settings.setCookieVersion(latestVer(COOKIE_DIFF_BASE));
        } else {
            Set<String> add = new HashSet<String>();
            Set<String> remove = new HashSet<String>();
            ver = diffSets(COOKIE_DIFF_BASE, ver, add, remove);
            updateCookie(settings, add, remove);
            settings.setCookieVersion(ver);
        }
    }

    private void updateCookie(SpywareSettings settings, Set<String> add,
                              Set<String> remove)
    {
        List<StringRule> rules = (List<StringRule>)settings.getCookieRules();
        if (null == rules) {
            rules = new LinkedList<StringRule>();
            settings.setCookieRules(rules);
        }

        for (Iterator<StringRule> i = rules.iterator(); i.hasNext(); ) {
            StringRule sr = i.next();
            if (remove.contains(sr.getString())) {
                i.remove();
                if (logger.isDebugEnabled()) {
                    logger.debug("removing cookie: " + sr.getString());
                }
            } else {
                remove.remove(sr);
                if (logger.isDebugEnabled()) {
                    logger.debug("not cookie: " + sr.getString());
                }
            }
        }

        for (String s : add) {
            rules.add(new StringRule(s));
            if (logger.isDebugEnabled()) {
                logger.debug("added cookie: " + s);
            }
        }
    }

    private void updateSubnet(SpywareSettings settings)
    {
        int ver = settings.getSubnetVersion();

        if (0 > ver || null == settings.getSubnetRules()) {
            List l = settings.getSubnetRules();
            if (null != l) {
                l.clear();
            }

            Set<String> add = initList(SUBNET_LIST);
            Set<String> remove = Collections.emptySet();
            updateSubnet(settings, add, remove);
            settings.setSubnetVersion(latestVer(SUBNET_DIFF_BASE));
        } else {
            Set<String> add = new HashSet<String>();
            Set<String> remove = new HashSet<String>();
            ver = diffSets(SUBNET_DIFF_BASE, ver, add, remove);
            updateSubnet(settings, add, remove);
            settings.setSubnetVersion(ver);
        }
    }

    private void updateSubnet(SpywareSettings settings, Set<String> add,
                              Set<String> rem)
    {
        Set<IPMaddrRule> remove = new HashSet<IPMaddrRule>();
        for (String s : rem) {
            IPMaddrRule imr = makeIPMAddrRule(s);
            if (null != imr) {
                remove.add(imr);
            }
        }

        List<IPMaddrRule> rules = (List<IPMaddrRule>)settings.getSubnetRules();
        if (null == rules) {
            rules = new LinkedList<IPMaddrRule>();
            settings.setSubnetRules(rules);
        }

        for (Iterator<IPMaddrRule> i = rules.iterator(); i.hasNext(); ) {
            IPMaddrRule imr = i.next();

            if (remove.contains(imr)) {
                i.remove();
                if (logger.isDebugEnabled()) {
                    logger.debug("removed subnet: " + imr.getIpMaddr());
                }
            } else {
                remove.remove(imr);
                if (logger.isDebugEnabled()) {
                    logger.debug("not removed subnet: " + imr.getIpMaddr());
                }
            }
        }

        for (String s : add) {
            IPMaddrRule imr = makeIPMAddrRule(s);
            if (null != imr) {
                rules.add(imr);
                if (logger.isDebugEnabled()) {
                    logger.debug("added subnet: " + s);
                }
            }
        }
    }

    private Set<String> initList(String file)
    {
        Set<String> s = new HashSet<String>();

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            for (String l = br.readLine(); null != l; l = br.readLine()) {
                s.add(l);
            }
        } catch (IOException exn) {
            logger.error("could not read list: " + file, exn);
        }

        return s;
    }

    private IPMaddrRule makeIPMAddrRule(String line)
    {
        StringTokenizer tok = new StringTokenizer(line, ":,");

        String addr = tok.nextToken();
        String description = tok.nextToken();
        String name = tok.hasMoreTokens() ? tok.nextToken() : "[no name]";

        IPMaddr maddr;
        try {
            maddr = IPMaddr.parse(addr);
            int i = maddr.maskNumBits(); /* if bad subnet throws exception */
        } catch (Exception e) {
            return null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("ADDING subnet Rule: " + addr);
        }
        IPMaddrRule rule = new IPMaddrRule(maddr, name, "[no category]", description);
        rule.setLog(true);
        rule.setLive(false);

        return rule;
    }

    private int diffSets(String diffBase, int startVersion,
                         Set<String> add, Set<String> remove)
    {
        for (int i = startVersion + 1; ; i++) {
            String r = diffBase + i;
            InputStream is = getClass().getClassLoader().getResourceAsStream(r);

            if (null == is) {
                return i - 1;
            }

            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                for (String l = br.readLine(); null != l; l = br.readLine()) {
                    if (l.startsWith("<")) {
                        String s = l.substring(2);
                        add.remove(s);
                        remove.add(s);
                    } else if (l.startsWith(">")) {
                        String s = l.substring(2);
                        add.add(s);
                        remove.remove(s);
                    }
                }
            } catch (IOException exn) {
                logger.error("could not make diffs: " + diffBase, exn);
            }
        }
    }

    private int latestVer(String diffBase)
    {
        for (int i = 0; ; i++) {
            URL u = getClass().getClassLoader().getResource(diffBase + i);
            if (null == u) {
                return i;
            }
        }
    }

    // XXX factor out this shit
    private static synchronized void deployWebAppIfRequired(Logger logger) {
        if (0 != deployCount++) {
            return;
        }

        LocalUvmContext mctx = LocalUvmContextFactory.context();
        LocalAppServerManager asm = mctx.appServerManager();

        Valve v = new OutsideValve()
            {
                protected boolean isInsecureAccessAllowed()
                {
                    return true;
                }

                /* Unified way to determine which parameter to check */
                protected boolean isOutsideAccessAllowed()
                {
                    return false;
                }

                /* Unified way to determine which parameter to check */
                protected String outsideErrorMessage()
                {
                    return "off-site access";
                }

                protected String httpErrorMessage()
                {
                    return "standard access";
                }
            };

        if (asm.loadInsecureApp("/spyware", "spyware", v)) {
            logger.debug("Deployed Spyware WebApp");
        } else {
            logger.error("Unable to deploy Spyware WebApp");
        }
    }

    // XXX factor out this shit
    private static synchronized void unDeployWebAppIfRequired(Logger logger) {
        if (0 != --deployCount) {
            return;
        }

        LocalUvmContext mctx = LocalUvmContextFactory.context();
        LocalAppServerManager asm = mctx.appServerManager();

        if (asm.unloadWebApp("/spyware")) {
            logger.debug("Unloaded Spyware WebApp");
        } else {
            logger.warn("Unable to unload Spyware WebApp");
        }
    }

    // XXX avoid
    private void reconfigure()
    {
        logger.info("Reconfigure.");
        if (this.settings.getSpywareEnabled()) {
            streamHandler.subnetList(this.settings.getSubnetRules());
        }

        List<StringRule> l = (List<StringRule>)settings.getActiveXRules();
        if (null != l) {
            Map<String, StringRule> s = new HashMap<String, StringRule>();
            for (StringRule sr : l) {
                s.put(sr.getString(), sr);
            }
            activeXRules = s;
        } else {
            activeXRules = null;
        }

        l = (List<StringRule>)settings.getCookieRules();
        if (null != l) {
            Map<String, StringRule> s = new HashMap<String, StringRule>();
            for (StringRule sr : l) {
                s.put(sr.getString(), sr);
            }
            cookieRules = s;
        } else {
            cookieRules = null;
        }

        Set<String> s = new HashSet<String>();
        l = (List<StringRule>)settings.getDomainWhitelist();
        for (StringRule sr : l) {
            if (sr.isLive()) {
                String str = normalizeDomain(sr.getString());

                s.add(str);
            }
        }
        domainWhitelist = s;
    }

    private String normalizeDomain(String dom)
    {
        String url = dom.toLowerCase();
        String uri = url.startsWith("http://")
            ? url.substring("http://".length()) : url;

        while (0 < uri.length()
               && ('*' == uri.charAt(0) || '.' == uri.charAt(0))) {
            uri = uri.substring(1);
        }

        if (uri.startsWith("www.")) {
            uri = uri.substring("www.".length());
        }

        int i = uri.indexOf('/');
        if (0 <= i) {
            uri = uri.substring(0, i);
        }

        return uri;
    }

    private List<String> getSpywareLists()
    {
        List<String> l = new ArrayList<String>();

        try {
            HttpClient hc = new HttpClient();
            HttpMethod get = new GetMethod(new URL(BLACKLIST_HOME, "list").toString());
            int rc = hc.executeMethod(get);
            InputStream is = get.getResponseBodyAsStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            for (String s = br.readLine(); null != s; s = br.readLine()) {
                l.add(s);
            }
        } catch (IOException exn) {
            logger.warn("could not get listing", exn);
        }

        return l;
    }

    // XXX soon to be deprecated ----------------------------------------------

    public Object getSettings()
    {
        return getSpywareSettings();
    }

    public void setSettings(Object settings)
    {
        setSpywareSettings((SpywareSettings)settings);
    }
}
