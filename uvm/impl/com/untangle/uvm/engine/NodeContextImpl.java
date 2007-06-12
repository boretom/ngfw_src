/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.uvm.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.IllegalAccessException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.untangle.uvm.UvmContextFactory;
import com.untangle.uvm.UvmLocalContext;
import com.untangle.uvm.node.DeployException;
import com.untangle.uvm.node.Node;
import com.untangle.uvm.node.NodeContext;
import com.untangle.uvm.node.NodeDesc;
import com.untangle.uvm.node.NodeException;
import com.untangle.uvm.node.NodePreferences;
import com.untangle.uvm.node.NodeState;
import com.untangle.uvm.node.NodeStats;
import com.untangle.uvm.node.TooManyInstancesException;
import com.untangle.uvm.node.UndeployException;
import com.untangle.uvm.policy.Policy;
import com.untangle.uvm.security.Tid;
import com.untangle.uvm.tapi.IPSessionDesc;
import com.untangle.uvm.tapi.NodeBase;
import com.untangle.uvm.tapi.NodeListener;
import com.untangle.uvm.tapi.NodeStateChangeEvent;
import com.untangle.uvm.toolbox.MackageDesc;
import com.untangle.uvm.util.TransactionWork;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

// XXX decouple from NodeBase
class NodeContextImpl implements NodeContext
{
    private final Logger logger = Logger.getLogger(getClass());

    private static final URL[] URL_PROTO = new URL[0];

    private final NodeDesc nodeDesc;
    private final Tid tid;
    private final NodePreferences nodePreferences;
    private final NodePersistentState persistentState;
    private final boolean isNew;

    private NodeBase node;
    private String mackageName;

    private final NodeManagerImpl nodeManager;
    private final ToolboxManagerImpl toolboxManager;

    NodeContextImpl(URLClassLoader classLoader, NodeDesc tDesc,
                         String mackageName, boolean isNew)
        throws DeployException
    {
        UvmContextImpl mctx = UvmContextImpl.getInstance();

        if (null != tDesc.getNodeBase()) {
            mctx.schemaUtil().initSchema("settings", tDesc.getNodeBase());
        }
        mctx.schemaUtil().initSchema("settings", tDesc.getName());

        nodeManager = mctx.nodeManager();
        toolboxManager = mctx.toolboxManager();

        LoggingManagerImpl lm = mctx.loggingManager();
        if (null != tDesc.getNodeBase()) {
            lm.initSchema(tDesc.getNodeBase());
        }
        lm.initSchema(tDesc.getName());

        this.nodeDesc = tDesc;
        this.tid = nodeDesc.getTid();
        this.mackageName = mackageName;
        this.isNew = isNew;

        checkInstanceCount(nodeDesc);

        if (isNew) {
            // XXX this isn't supposed to be meaningful:
            byte[] pKey = new byte[]
                { (byte)(tid.getId() & 0xFF),
                  (byte)((tid.getId() >> 8) & 0xFF) };


            persistentState = new NodePersistentState
                (tid, mackageName, pKey);

            nodePreferences = new NodePreferences(tid);

            TransactionWork tw = new TransactionWork()
                {
                    public boolean doWork(Session s)
                    {
                        s.save(persistentState);
                        s.save(nodePreferences);
                        return true;
                    }

                    public Object getResult() { return null; }
                };
            mctx.runTransaction(tw);
        } else {
            LoadSettings ls = new LoadSettings(tid);
            mctx.runTransaction(ls);
            this.persistentState = ls.getPersistentState();
            this.nodePreferences = ls.getNodePreferences();
        }

        logger.info("Creating node context for: " + tid
                    + " (" + nodeDesc.getName() + ")");
    }

    void init(String[] args) throws DeployException
    {
        Set<NodeContext>parentCtxs = new HashSet<NodeContext>();
        List<String> parents = nodeDesc.getParents();
        for (String parent : parents) {
            parentCtxs.add(startParent(parent, tid.getPolicy()));
        }

        final UvmLocalContext mctx = UvmContextFactory.context();
        try {
            nodeManager.registerThreadContext(this);

            String tidName = tid.getName();
            logger.debug("setting node " + tidName + " log4j repository");

            String className = nodeDesc.getClassName();
            node = (NodeBase)Class.forName(className).newInstance();

            for (NodeContext parentCtx : parentCtxs) {
                node.addParent((NodeBase)parentCtx.node());
            }

            node.addNodeListener(new NodeListener()
                {
                    public void stateChange(NodeStateChangeEvent te) {
                        {
                            final NodeState ts = te.getNodeState();

                            TransactionWork tw = new TransactionWork()
                                {
                                    public boolean doWork(Session s)
                                    {
                                        persistentState.setTargetState(ts);
                                        s.merge(persistentState);
                                        return true;
                                    }

                                    public Object getResult() { return null; }
                                };
                            mctx.runTransaction(tw);

                            mctx.eventLogger().log(new NodeStateChange(tid, ts));
                        }
                    }
                });

            if (isNew) {
                node.initializeSettings();
                node.init(args);
                boolean enabled = toolboxManager.isEnabled(mackageName);
                if (!enabled) {
                    node.disable();
                }
            } else {
                node.resumeState(persistentState.getTargetState(), args);
            }
        } catch (ClassNotFoundException exn) {
            throw new DeployException(exn);
        } catch (InstantiationException exn) {
            throw new DeployException(exn);
        } catch (IllegalAccessException exn) {
            throw new DeployException(exn);
        } catch (NodeException exn) {
            throw new DeployException(exn);
        } finally {
            nodeManager.deregisterThreadContext();

            if (null == node) {
                TransactionWork tw = new TransactionWork()
                    {
                        public boolean doWork(Session s)
                        {
                            s.delete(persistentState);
                            return true;
                        }

                        public Object getResult() { return null; }
                    };
                mctx.runTransaction(tw);
            }

        }
    }

    // NodeContext -------------------------------------------------------

    public Tid getTid()
    {
        return tid;
    }

    public NodeDesc getNodeDesc()
    {
        return nodeDesc;
    }

    public NodePreferences getNodePreferences()
    {
        return nodePreferences;
    }

    public MackageDesc getMackageDesc()
    {
        return toolboxManager.mackageDesc(mackageName);
    }

    public Node node()
    {
        return node;
    }

    // node call-through methods -----------------------------------------

    public IPSessionDesc[] liveSessionDescs()
    {
        return node.liveSessionDescs();
    }

    public NodeState getRunState()
    {
        return null == node ? NodeState.LOADED
            : node.getRunState();
    }

    public NodeStats getStats()
    {
        return node.getStats();
    }

    // XXX should be LocalNodeContext ------------------------------------

    // XXX remove this method...
    @Deprecated
    public boolean runTransaction(TransactionWork tw)
    {
        return UvmContextFactory.context().runTransaction(tw);
    }

    public InputStream getResourceAsStream(String res)
    {
        try {
            URL url = new URL(toolboxManager.getResourceDir(getMackageDesc()),
                              res);
            File f = new File(url.toURI());
            return new FileInputStream(f);
        } catch (MalformedURLException exn) {
            logger.warn("could not not be found: " + res, exn);
            return null;
        } catch (URISyntaxException exn) {
            logger.warn("could not not be found: " + res, exn);
            return null;
        } catch (FileNotFoundException exn) {
            logger.warn("could not not be found: " + res, exn);
            return null;
        }
    }

    // package private methods ------------------------------------------------

    void destroy() throws UndeployException
    {
        try {
            nodeManager.registerThreadContext(this);
            if (node.getRunState() == NodeState.RUNNING) {
                node.stop();
            }
            node.destroy();
            node.destroySettings();
        } catch (NodeException exn) {
            throw new UndeployException(exn);
        } finally {
            nodeManager.deregisterThreadContext();
        }
    }

    void unload()
    {
        if (node != null) {
            try {
                nodeManager.registerThreadContext(this);
                node.unload();
            } finally {
                nodeManager.deregisterThreadContext();
            }
        }
    }

    void destroyPersistentState()
    {
        TransactionWork tw = new TransactionWork()
            {
                public boolean doWork(Session s)
                {
                    tid.setPolicy(null);
                    s.update(tid);
                    s.delete(persistentState);
                    s.delete(getNodePreferences());
                    return true;
                }

                public Object getResult() { return null; }
            };
        UvmContextFactory.context().runTransaction(tw);
    }

    // private classes --------------------------------------------------------

    private class LoadSettings extends TransactionWork
    {
        private final Tid tid;

        private NodePersistentState persistentState;
        private NodePreferences nodePreferences;

        public LoadSettings(Tid tid)
        {
            this.tid = tid;
        }

        public boolean doWork(Session s)
        {
            Query q = s.createQuery
                ("from NodePersistentState tps where tps.tid = :tid");
            q.setParameter("tid", tid);

            persistentState = (NodePersistentState)q.uniqueResult();

            if (!toolboxManager.isEnabled(mackageName)) {
                persistentState.setTargetState(NodeState.DISABLED);
                s.merge(persistentState);
            } else if (NodeState.DISABLED == persistentState.getTargetState()) {
                persistentState.setTargetState(NodeState.INITIALIZED);
                s.merge(persistentState);
            }

            q = s.createQuery
                ("from NodePreferences tp where tp.tid = :tid");
            q.setParameter("tid", tid);
            nodePreferences = (NodePreferences)q.uniqueResult();
            return true;
        }

        public Object getResult() { return null; }

        public NodePersistentState getPersistentState()
        {
            return persistentState;
        }

        public NodePreferences getNodePreferences()
        {
            return nodePreferences;
        }
    }

    // private methods --------------------------------------------------------

    private void checkInstanceCount(NodeDesc nodeDesc)
        throws TooManyInstancesException
    {
        if (nodeDesc.isSingleInstance()) {
            String n = nodeDesc.getName();
            Policy p = nodeDesc.getTid().getPolicy();
            List<Tid> l = nodeManager.nodeInstances(n, p);

            if (1 == l.size()) {
                if (!tid.equals(l.get(0))) {
                    throw new TooManyInstancesException("too many instances: " + n);
                }
            } else if (1 < l.size()) {
                throw new TooManyInstancesException("too many instances: " + n);
            }
        }
    }

    private void addTid(Object o)
    {
        try {
            Method m = o.getClass().getMethod("setTid", Tid.class);
            m.invoke(o, tid);
        } catch (NoSuchMethodException exn) {
            /* no setTid(Tid) method, nothing to do */
            return;
        } catch (SecurityException exn) {
            logger.warn(exn); /* shouldn't happen */
        } catch (IllegalAccessException exn) {
            logger.warn(exn); /* shouldn't happen */
        } catch (IllegalArgumentException exn) {
            logger.warn(exn); /* shouldn't happen */
        } catch (InvocationTargetException exn) {
            logger.warn(exn); /* shouldn't happen */
        }
    }

    private NodeContext startParent(String parent, Policy policy)
        throws DeployException
    {
        if (null == parent) {
            return null;
        }

        MackageDesc md = toolboxManager.mackageDesc(parent);
        if (md.isService()) {
            policy = null;
        }

        logger.debug("Starting parent: " + parent + " for: " + tid);

        NodeContext pctx = getParentContext(parent);

        if (null == pctx) {
            logger.debug("Parent does not exist, instantiating");

            try {
                Tid parentTid = nodeManager.instantiate(parent, policy);
                pctx = nodeManager.nodeContext(parentTid);
            } catch (TooManyInstancesException exn) {
                pctx = getParentContext(parent);
            }
        }

        if (null == pctx) {
            throw new DeployException("could not create parent: " + parent);
        } else {
            return pctx;
        }
    }

    private NodeContext getParentContext(String parent)
    {
        for (Tid t : nodeManager.nodeInstances(parent)) {
            Policy p = t.getPolicy();
            if (null == p || p.equals(tid.getPolicy())) {
                return nodeManager.nodeContext(t);
            }

        }

        return null;
    }

    // Object methods ---------------------------------------------------------

    public String toString()
    {
        return "NodeContext tid: " + tid
            + " (" + nodeDesc.getName() + ")";
    }
}
