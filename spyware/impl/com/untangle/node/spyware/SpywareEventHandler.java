/*
 * $Id$
 */
package com.untangle.node.spyware;

import java.util.Iterator;
import java.util.Set;
import java.util.List;

import com.untangle.node.util.IPSet;
import com.untangle.node.util.IPSetTrie;
import com.untangle.uvm.node.GenericRule;
import com.untangle.uvm.node.IPMaskedAddress;
import com.untangle.uvm.vnet.AbstractEventHandler;
import com.untangle.uvm.vnet.IPNewSessionRequest;
import com.untangle.uvm.vnet.NodeSession;
import com.untangle.uvm.vnet.TCPNewSessionRequest;
import com.untangle.uvm.vnet.UDPNewSessionRequest;
import com.untangle.uvm.vnet.event.TCPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.TCPSessionEvent;
import com.untangle.uvm.vnet.event.UDPNewSessionRequestEvent;
import com.untangle.uvm.vnet.event.UDPSessionEvent;
import org.apache.log4j.Logger;

public class SpywareEventHandler extends AbstractEventHandler
{
    private final Logger logger = Logger.getLogger(getClass());

    private final SpywareImpl node;

    private IPSet subnetSet  = null;

    public SpywareEventHandler(SpywareImpl node)
    {
        super(node);

        this.node = node;
    }

    public void subnetList(List<GenericRule> list)
    {
        if (null == list) {
            subnetSet = null;
        } else {
            IPSetTrie set = new IPSetTrie();

            for (Iterator<GenericRule> i = list.iterator(); i.hasNext(); ) {
                GenericRule rule = i.next();
                IPMaskedAddress ipm = new IPMaskedAddress(rule.getString());
                try {
                    ipm.bitString();
                } catch (Exception e) {
                    logger.error("BAD RULE: " + rule.getString(), e);
                }
                set.add(ipm,rule);
            }

            this.subnetSet = set;
        }
    }

    public void handleTCPNewSessionRequest(TCPNewSessionRequestEvent event)
    {
        if (null != subnetSet) {
            detectSpyware(event.sessionRequest(), true);
        } else {
            logger.debug("spyware detection disabled");
        }
    }

    public void handleUDPNewSessionRequest(UDPNewSessionRequestEvent event)
    {
        if (null != subnetSet) {
            detectSpyware(event.sessionRequest(), true);
        } else {
            logger.debug("spyware detection disabled");
        }
    }

    @Override
    public void handleTCPComplete(TCPSessionEvent event)
    {
        NodeSession s = event.session();
        SpywareAccessEvent spe = (SpywareAccessEvent)s.attachment();
        if (null != spe) {
            node.logEvent(spe);
        }
    }

    @Override
    public void handleUDPComplete(UDPSessionEvent event)
    {
        NodeSession s = event.session();
        SpywareAccessEvent spe = (SpywareAccessEvent)s.attachment();
        if (null != spe) {
            node.logEvent(spe);
        }
    }

    void detectSpyware(IPNewSessionRequest ipr, boolean release)
    {
        IPMaskedAddress ipm = new IPMaskedAddress(ipr.getServerAddr().getHostAddress());

        GenericRule ir = (GenericRule)this.subnetSet.getMostSpecific(ipm);

        if (ir == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Subnet scan: " + ipm.toString() + " -> clean.");
            }
            if (release) { ipr.release(); }
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Subnet scan: " + ipm.toString() + " -> DETECTED.");
        }

        node.incrementSubnetScan();

        if (logger.isDebugEnabled()) {
            logger.debug("-------------------- Detected Subnet --------------------");
            logger.debug("Subnet Name  : " + ir.getName());
            logger.debug("Host          : " + ipr.getClientAddr().getHostAddress() + ":" + ipr.getClientPort());
            logger.debug("Suspicious IP : " + ipr.getServerAddr().getHostAddress() + ":" + ipr.getServerPort());
            logger.debug("Matches       : " + ir.getString());
            if (ipr instanceof TCPNewSessionRequest)
                logger.debug("Protocol      : TCP");
            if (ipr instanceof UDPNewSessionRequest)
                logger.debug("Protocol      : UDP");
            logger.debug("----------------------------------------------------------");
        }

        SpywareAccessEvent evt = new SpywareAccessEvent(ipr.sessionEvent(), ir.getName(), new IPMaskedAddress(ir.getString()), Boolean.FALSE);
        ipr.attach(evt);

        if (release) { ipr.release(true); }
    }
}
