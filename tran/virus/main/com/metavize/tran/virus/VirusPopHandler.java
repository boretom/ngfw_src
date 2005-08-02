
/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.virus;

import java.io.File;
import java.io.IOException;

import com.metavize.mvvm.argon.IntfConverter;
import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.tran.mail.papi.pop.PopStateMachine;
import com.metavize.tran.mail.papi.MIMEMessageT;
import com.metavize.tran.mail.papi.WrappedMessageGenerator;
import com.metavize.tran.mime.HeaderParseException;
import com.metavize.tran.mime.MIMEMessage;
import com.metavize.tran.mime.MIMEPart;
import com.metavize.tran.mime.MIMEUtil;
import com.metavize.tran.token.Token;
import com.metavize.tran.token.TokenException;
import com.metavize.tran.token.TokenResult;
import com.metavize.tran.util.FileFactory;
import com.metavize.tran.util.TempFileFactory;
import org.apache.log4j.Logger;

public class VirusPopHandler extends PopStateMachine
{
    private final static Logger logger = Logger.getLogger(VirusPopHandler.class);
    private final static Logger eventLogger = MvvmContextFactory.context().eventLogger();

    private final VirusScanner zScanner;
    private final String zVendorName;

    private final WrappedMessageGenerator zWMsgGenerator;
    private final VirusMessageAction zMsgAction;
    private final boolean bScan;

    // constructors -----------------------------------------------------------

    VirusPopHandler(TCPSession session, VirusTransformImpl transform)
    {
        super(session);

        zScanner = transform.getScanner();
        zVendorName = zScanner.getVendorName();

        VirusPOPConfig zConfig;
        WrappedMessageGenerator zWMGenerator;
        if (IntfConverter.INSIDE == session.clientIntf()) {
            zConfig = transform.getVirusSettings().getPOPInbound();
            zWMGenerator = new WrappedMessageGenerator(VirusSettings.IN_MOD_SUB_TEMPLATE, VirusSettings.IN_MOD_BODY_TEMPLATE);
        } else {
            zConfig = transform.getVirusSettings().getPOPOutbound();
            zWMGenerator = new WrappedMessageGenerator(VirusSettings.OUT_MOD_SUB_TEMPLATE, VirusSettings.OUT_MOD_BODY_TEMPLATE);
        }
        bScan = zConfig.getScan();
        zMsgAction = zConfig.getMsgAction();
        zWMsgGenerator = zWMGenerator;
        //logger.debug("scan: " + bScan + ", message action: " + zMsgAction);
    }

    // PopStateMachine methods -----------------------------------------------

    protected TokenResult scanMessage() throws TokenException
    {
        MIMEPart azMPart[];

        if (true == bScan &&
            MIMEUtil.EMPTY_MIME_PARTS != (azMPart = MIMEUtil.getCandidateParts(zMMessage))) {
            TempFileFactory zTFFactory = new TempFileFactory();
            VirusScannerResult zFirstResult = null;

            VirusScannerResult zCurResult;
            File zMPFile;
            boolean bWrap;

            for (MIMEPart zMPart : azMPart) {
                if (true == MIMEUtil.shouldScan(zMPart)) {
                    try {
                        zMPFile = zMPart.getContentAsFile(zTFFactory, true);
                    } catch (IOException exn) {
                        throw new TokenException("cannot get message/mime part file: ", exn);
                    }

                    if (null != (zCurResult = scanFile(zMPFile)) &&
                        VirusMessageAction.REMOVE == zMsgAction) {
                        try {
                            MIMEUtil.removeChild(zMPart);
                        } catch (HeaderParseException exn) {
                            throw new TokenException("cannot remove message/mime part containing virus: ", exn);
                        }

                        if (null == zFirstResult) {
                            /* use 1st scan result to wrap message */
                            zFirstResult = zCurResult;
                        }
                    }
                }
            }

            if (null != zFirstResult) {
                /* wrap infected message and rebuild message token */
                MIMEMessage zWMMessage = zWMsgGenerator.wrap(zMMessage, zFirstResult);
                try {
                    zMsgFile = zWMMessage.toFile(new FileFactory() {
                        public File createFile(String name) throws IOException {
                          return createFile();
                        }

                        public File createFile() throws IOException {
                          return getPipeline().mktemp();
                        }
                    } );

                    zMMessageT = new MIMEMessageT(zMsgFile);
                    zMMessageT.setMIMEMessage(zWMMessage);

                    /* dispose original message
                     * (will discard remaining references during reset)
                     */
                    zMMessage.dispose();
                } catch (IOException exn) {
                    throw new TokenException("cannot wrap original message/mime part: ", exn);
                }
            }
        }
        //else {
            //logger.debug("scan is not enabled or message contains no MIME parts");
        //}

        return new TokenResult(new Token[] { zMMessageT }, null);
    }

    private VirusScannerResult scanFile(File zFile) throws TokenException
    {
        try {
            VirusScannerResult zScanResult = zScanner.scanFile(zFile.getPath());

            eventLogger.info(new VirusMailEvent(zMsgInfo, zScanResult, zMsgAction, zVendorName));

            if (false == zScanResult.isClean()) {
                return zScanResult;
            }
            /* else not infected - discard scan result */

            return null;
        } catch (IOException exn) {
            throw new TokenException("cannot scan message/mime part file: ", exn);
        }
        catch (InterruptedException exn) { // XXX deal with this in scanner
            throw new TokenException("scan interrupted: ", exn);
        }
    }
}
