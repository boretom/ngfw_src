/*
 * Copyright (c) 2003, 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.virus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.argon.SessionMatcher;
import com.metavize.mvvm.tapi.AbstractTransform;
import com.metavize.mvvm.tapi.Affinity;
import com.metavize.mvvm.tapi.Fitting;
import com.metavize.mvvm.tapi.IPSessionDesc;
import com.metavize.mvvm.tapi.PipeSpec;
import com.metavize.mvvm.tapi.PipelineFoundry;
import com.metavize.mvvm.tapi.Protocol;
import com.metavize.mvvm.tapi.SoloPipeSpec;
import com.metavize.mvvm.tapi.Subscription;
import com.metavize.mvvm.tapi.TransformContextFactory;
import com.metavize.mvvm.tran.Direction;
import com.metavize.mvvm.tran.Interface;
import com.metavize.mvvm.tran.MimeType;
import com.metavize.mvvm.tran.MimeTypeRule;
import com.metavize.mvvm.tran.StringRule;
import com.metavize.tran.mail.SMTPNotifyAction;
import com.metavize.tran.token.TokenAdaptor;
import net.sf.hibernate.HibernateException;
import net.sf.hibernate.Query;
import net.sf.hibernate.Session;
import net.sf.hibernate.Transaction;
import org.apache.log4j.Logger;

public class VirusTransformImpl extends AbstractTransform
    implements VirusTransform
{
    private static final String FTP_QUERY
        = "SELECT create_date, "
        +        "'FTP' AS type, "
        +        "'file' AS location, "
        +        "NOT clean AS infected, "
        +        "CASE WHEN clean THEN 'PASSED' "
        +             "WHEN virus_cleaned THEN 'CLEANED' "
        +             "ELSE 'BLOCKED' "
        +        "END AS action, "
        +        "virus_name, "
        +        "c_client_addr, c_client_port, s_server_addr, s_server_port, "
        +        "client_intf, server_intf "
        + "FROM tr_virus_evt evt "
        + "JOIN pl_endp USING (session_id) "
        + "WHERE vendor_name = ? "
        + "ORDER BY create_date DESC LIMIT ?";

    private static final String HTTP_QUERY
        = "SELECT create_date, "
        +        "'HTTP' AS type, "
        +        "'http://' || host || uri AS location, "
        +        "NOT clean AS infected, "
        +        "CASE WHEN clean THEN 'PASSED' "
        +             "WHEN virus_cleaned THEN 'CLEANED' "
        +             "ELSE 'BLOCKED' "
        +        "END AS action, "
        +        "virus_name, "
        +        "c_client_addr, c_client_port, s_server_addr, s_server_port, "
        +        "client_intf, server_intf "
        + "FROM tr_virus_evt_http evt "
        + "JOIN tr_http_evt_req req ON evt.request_line = req.request_id "
        + "JOIN pl_endp endp ON req.session_id = endp.session_id "
        + "JOIN tr_http_req_line rl ON rl.request_id = req.request_id "
        + "WHERE vendor_name = ? "
        + "ORDER BY req.time_stamp DESC LIMIT ?";

    private static final String MAIL_QUERY
        = "SELECT create_date, "
        +        "'MAIL' AS type, "
        +        "subject AS location, "
        +        "NOT clean AS infected, "
        +        "CASE WHEN msg_action = 'P' THEN 'PASSED' "
        +             "WHEN msg_action = 'R' THEN 'REMOVED' "
        +        "END AS action, "
        +        "virus_name, "
        +        "c_client_addr, c_client_port, s_server_addr, s_server_port, "
        +        "client_intf, server_intf "
        + "FROM tr_virus_evt_mail evt "
        + "JOIN tr_mail_message_info info ON evt.msg_id = info.id "
        + "JOIN pl_endp endp ON info.session_id = endp.session_id "
        + "WHERE vendor_name = ? "
        + "ORDER BY create_date DESC LIMIT ?";

    private static final String SMTP_QUERY
        = "SELECT create_date, "
        +        "'MAIL' AS type, "
        +        "subject AS location, "
        +        "NOT clean AS infected, "
        +        "CASE WHEN msg_action = 'P' THEN 'PASSED' "
        +             "WHEN msg_action = 'R' THEN 'REMOVED' "
        +             "WHEN msg_action = 'B' THEN 'BLOCKED' "
        +        "END AS action, "
        +        "virus_name, "
        +        "c_client_addr, c_client_port, s_server_addr, s_server_port, "
        +        "client_intf, server_intf "
        + "FROM tr_virus_evt_smtp evt "
        + "JOIN tr_mail_message_info info ON evt.msg_id = info.id "
        + "JOIN pl_endp endp ON info.session_id = endp.session_id "
        + "WHERE vendor_name = ? "
        + "ORDER BY create_date DESC LIMIT ?";

    private static final String[] QUERIES
        = { FTP_QUERY, HTTP_QUERY, MAIL_QUERY, SMTP_QUERY };

    private static final PipelineFoundry FOUNDRY = MvvmContextFactory.context()
        .pipelineFoundry();

    private static final int FTP = 0;
    private static final int HTTP = 1;
    private static final int SMTP = 2;
    private static final int POP = 3;

    private final VirusScanner scanner;

    private final PipeSpec[] pipeSpecs = new PipeSpec[] {
        new SoloPipeSpec("virus-ftp", this,
                         new TokenAdaptor(new VirusFtpFactory(this)),
                         Fitting.FTP_TOKENS, Affinity.SERVER, 0),
        new SoloPipeSpec("virus-http", this,
                         new TokenAdaptor(new VirusHttpFactory(this)),
                         Fitting.HTTP_TOKENS, Affinity.SERVER, 0),
        new SoloPipeSpec("virus-smtp", this,
                         new TokenAdaptor(new VirusSmtpFactory(this)),
                         Fitting.SMTP_TOKENS, Affinity.SERVER, 0),
        new SoloPipeSpec("virus-pop", this,
                         new TokenAdaptor(new VirusPopFactory(this)),
                         Fitting.POP_TOKENS, Affinity.SERVER, 0)
        };

    private final Logger logger = Logger.getLogger(VirusTransformImpl.class);

    private VirusSettings settings;

    private static final SessionMatcher VIRUS_SESSION_MATCHER = new SessionMatcher() {
            /* Kill all sessions on ports 20, 21 and 80 */
            public boolean isMatch(com.metavize.mvvm.argon.IPSessionDesc session)
            {
                /* Don't kill any UDP Sessions */
                if (session.protocol() == com.metavize.mvvm.argon.IPSessionDesc.IPPROTO_UDP) {
                    return false;
                }

                int clientPort = session.clientPort();
                int serverPort = session.serverPort();

                /* FTP responds on port 20, server is on 21, HTTP server is on 80 */
                if (clientPort == 20 || serverPort == 21 || serverPort == 80 || serverPort == 20) {
                    return true;
                }

                /* email SMTP (25) / POP3 (110) / IMAP (143) */
                if (serverPort == 25 || serverPort == 110 || serverPort == 143) {
                    return true;
                }

                return false;
            }
        };

    // constructors -----------------------------------------------------------

    public VirusTransformImpl(VirusScanner scanner)
    {
        this.scanner = scanner;
    }

    // VirusTransform methods -------------------------------------------------

    public void setVirusSettings(VirusSettings settings)
    {
        Session s = TransformContextFactory.context().openSession();
        try {
            Transaction tx = s.beginTransaction();

            s.saveOrUpdateCopy(settings);
            this.settings = settings;

            tx.commit();
        } catch (HibernateException exn) {
            logger.warn("could not get VirusSettings", exn);
        } finally {
            try {
                s.close();
            } catch (HibernateException exn) {
                logger.warn("could not close hibernate session", exn);
            }
        }

        virusReconfigure();
    }

    public VirusSettings getVirusSettings()
    {
        return settings;
    }

    public List<VirusLog> getEventLogs(int limit)
    {
        List<VirusLog> l = new ArrayList<VirusLog>(4 * limit);

        for (String q : QUERIES) {
            getEventLogs(q, l, limit);
        }

        Collections.sort(l);

        return new ArrayList(l.subList(0, Math.min(limit, l.size())));
    }

    // Transform methods ------------------------------------------------------

    private void virusReconfigure()
    {
        // FTP
        Set subscriptions = new HashSet();
        {
            Subscription subscription = new Subscription
                (Protocol.TCP, Interface.ANY, Interface.ANY);
            subscriptions.add(subscription);
        }

        pipeSpecs[FTP].setSubscriptions(subscriptions);

        // HTTP
        subscriptions = new HashSet();
        if (settings.getHttpInbound().getScan()) {
            // XXX i get -2 on my home machine
            Subscription subscription = new Subscription
                (Protocol.TCP, Interface.INSIDE, Interface.OUTSIDE);
            subscriptions.add(subscription);
        }

        if (settings.getHttpOutbound().getScan()) {
            // XXX i get -2 on my home machine
            Subscription subscription = new Subscription
                (Protocol.TCP, Interface.OUTSIDE, Interface.INSIDE );
            subscriptions.add(subscription);
        }

        pipeSpecs[HTTP].setSubscriptions(subscriptions);

        // SMTP
        subscriptions = new HashSet();
        {
            Subscription subscription = new Subscription
                (Protocol.TCP, Interface.ANY, Interface.ANY);
            subscriptions.add(subscription);
        }

        pipeSpecs[SMTP].setSubscriptions(subscriptions);

        // POP
        subscriptions = new HashSet();
        {
            Subscription subscription = new Subscription
                (Protocol.TCP, Interface.ANY, Interface.ANY);
            subscriptions.add(subscription);
        }

        pipeSpecs[POP].setSubscriptions(subscriptions);
    }

    // AbstractTransform methods ----------------------------------------------

    @Override
    protected PipeSpec[] getPipeSpecs()
    {
        return pipeSpecs;
    }

    protected void initializeSettings()
    {
        VirusSettings vs = new VirusSettings(getTid());
        vs.setHttpInbound(new VirusConfig(true, true, "Scan incoming HTTP files"));
        vs.setHttpOutbound(new VirusConfig(false, true, "Scan outgoing HTTP files" ));
        vs.setFtpInbound(new VirusConfig(true, true, "Scan incoming FTP files" ));
        vs.setFtpOutbound(new VirusConfig(false, true, "Scan outgoing FTP files" ));

        vs.setSMTPInbound(new VirusSMTPConfig(true, SMTPVirusMessageAction.REMOVE, SMTPNotifyAction.NEITHER, "Scan incoming SMTP e-mail" ));
        vs.setSMTPOutbound(new VirusSMTPConfig(false, SMTPVirusMessageAction.PASS, SMTPNotifyAction.NEITHER, "Scan outgoing SMTP e-mail" ));

        vs.setPOPInbound(new VirusPOPConfig(true, VirusMessageAction.REMOVE, "Scan incoming POP e-mail" ));
        vs.setPOPOutbound(new VirusPOPConfig(false, VirusMessageAction.PASS, "Scan outgoing POP e-mail" ));

        vs.setIMAPInbound(new VirusIMAPConfig(true, VirusMessageAction.REMOVE, "Scan incoming IMAP e-mail" ));
        vs.setIMAPOutbound(new VirusIMAPConfig(false, VirusMessageAction.PASS, "Scan outgoing IMAP e-mail" ));

        /**
         * FIXME, need list with booleans
         * default should be:
         * application/x-javascript.*    false
         * application/x-shockwave-flash false
         * application/.*" true
         * images/.* false
         * text/.*   false
         * video/.*  false
         * audio/.*  false
         */
        List s = new ArrayList();
        s.add(new MimeTypeRule(new MimeType("application/x-javascript"), "JavaScript", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-shockwave-flash"), "Shockwave Flash", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-director"), "Macromedia Shockwave", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/futuresplash"), "Macromedia FutureSplash", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-java-applet"), "Java Applet", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/rtf"), "Rich Text Format", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/pdf"), "Adobe Acrobat", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/postscript"), "Postscript", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/*"), "applications", "misc", true));
        s.add(new MimeTypeRule(new MimeType("image/*"), "images", "image", false));
        s.add(new MimeTypeRule(new MimeType("video/*"), "video", "video", false));
        s.add(new MimeTypeRule(new MimeType("text/*"), "text", "text", false));
        s.add(new MimeTypeRule(new MimeType("audio/*"), "audio", "audio", false));

        /*
         * For now we're going back to the short list.
        s.add(new MimeTypeRule(new MimeType("application/octet-stream"), "unspecified data", "byte stream", false));

        s.add(new MimeTypeRule(new MimeType("application/x-msdownload"), "Microsoft download", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/exe"), "executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-exe"), "executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/dos-exe"), "DOS executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-winexe"), "Windows executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/msdos-windows"), "MS-DOS executable", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-msdos-program"), "MS-DOS program", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-oleobject"), "Microsoft OLE Object", "executable", false));

        s.add(new MimeTypeRule(new MimeType("application/x-java-applet"), "Java Applet", "executable", false));

        s.add(new MimeTypeRule(new MimeType("audio/mpegurl"), "MPEG audio URLs", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mpegurl"), "MPEG audio URLs", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mp3"), "MP3 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mp3"), "MP3 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mpg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-mpg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/x-ogg"), "Ogg Vorbis", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/m4a"), "MPEG 4 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mp2"), "MP2 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mp1"), "MP1 audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/ogg"), "Ogg Vorbis", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/wav"), "Microsoft WAV", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-wav"), "Microsoft WAV", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-wav"), "Microsoft WAV", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/aac"), "Advanced Audio Coding", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/midi"), "MIDI audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/aiff"), "AIFF audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-aiff"), "AIFF audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-aiff"), "AIFF audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-windows-acm"), "Windows ACM", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-windows-pcm"), "Windows PCM", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/basic"), "8-bit u-law PCM", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-au"), "Sun audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/3gpp"), "3GPP", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/3gpp-encrypted"), "encrypted 3GPP", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/scpls"), "streaming mp3 playlists", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-scpls"), "streaming mp3 playlists", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/smil"), "SMIL", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/sdp"), "Streaming Download Project", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sdp"), "Streaming Download Project", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr"), "AMR codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr-encrypted"), "AMR encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr-wb"), "AMR-WB codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/amr-wb-encrypted"), "AMR-WB encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr"), "3GPP codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr-encrypted"), "3GPP-AMR encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr-wb"), "3gpp-AMR-WB codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-rn-3gpp-amr-wb-encrypted"), "3gpp-AMR_WB encrypted codec", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/streamingmedia"), "Streaming Media", "audio", false));

        s.add(new MimeTypeRule(new MimeType("video/mpeg"), "MPEG video", "video", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-ms-wma"), "Windows Media", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/quicktime"), "QuickTime", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/x-ms-asf"), "Microsoft ASF", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/x-msvideo"), "Microsoft AVI", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/x-sgi-mov"), "SGI movie", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/3gpp"), "3GPP video", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/3gpp-encrypted"), "3GPP encrypted video", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/3gpp2"), "3GPP2 video", "video", false));

        s.add(new MimeTypeRule(new MimeType("audio/x-realaudio"), "RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("text/vnd.rn-realtext"), "RealText", "text", false));
        s.add(new MimeTypeRule(new MimeType("audio/vnd.rn-realaudio"), "RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-pn-realaudio"), "RealAudio plug-in", "audio", false));
        s.add(new MimeTypeRule(new MimeType("image/vnd.rn-realpix"), "RealPix", "image", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realmedia"), "RealMedia", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realmedia-vbr"), "RealMedia VBR", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realmedia-secure"), "secure RealMedia", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realaudio-secure"), "secure RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("audio/x-realaudio-secure"), "secure RealAudio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("video/vnd.rn-realvideo-secure"), "secure RealVideo", "video", false));
        s.add(new MimeTypeRule(new MimeType("video/vnd.rn-realvideo"), "RealVideo", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realsystem-rmj"), "RealSystem media", "video", false));
        s.add(new MimeTypeRule(new MimeType("application/vnd.rn-realsystem-rmx"), "RealSystem secure media", "video", false));
        s.add(new MimeTypeRule(new MimeType("audio/rn-mpeg"), "MPEG audio", "audio", false));
        s.add(new MimeTypeRule(new MimeType("application/x-shockwave-flash"), "Macromedia Shockwave", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-director"), "Macromedia Shockwave", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-authorware-bin"), "Macromedia Authorware binary", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-authorware-map"), "Macromedia Authorware shocked file", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/x-authorware-seg"), "Macromedia Authorware shocked packet", "multimedia", false));
        s.add(new MimeTypeRule(new MimeType("application/futuresplash"), "Macromedia FutureSplash", "multimedia", false));

        s.add(new MimeTypeRule(new MimeType("application/zip"), "ZIP", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-lzh"), "LZH archive", "archive", false));

        s.add(new MimeTypeRule(new MimeType("image/gif"), "Graphics Interchange Format", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/png"), "Portable Network Graphics", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/jpeg"), "JPEG", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/bmp"), "Microsoft BMP", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/tiff"), "Tagged Image File Format", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/x-freehand"), "Macromedia Freehand", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/x-cmu-raster"), "CMU Raster", "image", false));
        s.add(new MimeTypeRule(new MimeType("image/x-rgb"), "RGB image", "image", false));

        s.add(new MimeTypeRule(new MimeType("text/css"), "cascading style sheet", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/html"), "HTML", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/plain"), "plain text", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/richtext"), "rich text", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/tab-separated-values"), "tab separated values", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/xml"), "XML", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/xsl"), "XSL", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/x-sgml"), "SGML", "text", false));
        s.add(new MimeTypeRule(new MimeType("text/x-vcard"), "vCard", "text", false));

        s.add(new MimeTypeRule(new MimeType("application/mac-binhex40"), "Macintosh BinHex", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-stuffit"), "Macintosh Stuffit archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/macwriteii"), "MacWrite Document", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/applefile"), "Macintosh File", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/mac-compactpro"), "Macintosh Compact Pro", "archive", false));

        s.add(new MimeTypeRule(new MimeType("application/x-bzip2"), "block compressed", "compressed", false));
        s.add(new MimeTypeRule(new MimeType("application/x-shar"), "shell archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-gtar"), "gzipped tar archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-gzip"), "gzip compressed", "compressed", false));
        s.add(new MimeTypeRule(new MimeType("application/x-tar"), "4.3BSD tar archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-ustar"), "POSIX tar archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-cpio"), "old cpio archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-bcpio"), "POSIX cpio archive", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sv4crc"), "System V cpio with CRC", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-compress"), "UNIX compressed", "compressed", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sv4cpio"), "System V cpio", "archive", false));
        s.add(new MimeTypeRule(new MimeType("application/x-sh"), "UNIX shell script", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-csh"), "UNIX csh script", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-tcl"), "Tcl script", "executable", false));
        s.add(new MimeTypeRule(new MimeType("application/x-javascript"), "JavaScript", "executable", false));

        s.add(new MimeTypeRule(new MimeType("application/x-excel"), "Microsoft Excel", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/mspowerpoint"), "Microsoft Powerpoint", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/msword"), "Microsoft Word", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/wordperfect5.1"), "Word Perfect", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/rtf"), "Rich Text Format", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/pdf"), "Adobe Acrobat", "document", false));
        s.add(new MimeTypeRule(new MimeType("application/postscript"), "Postscript", "document", false));
*/

        vs.setHttpMimeTypes(s);

        s = new ArrayList();
        /* XXX Need a description here */
        s.add(new StringRule("exe", "executable", "download" , true));
        s.add(new StringRule("com", "executable", "download", true));
        s.add(new StringRule("ocx", "executable", "ActiveX", true));
        s.add(new StringRule("dll", "executable", "ActiveX", true));
        s.add(new StringRule("cab", "executable", "ActiveX", true));
        s.add(new StringRule("bin", "executable", "download", true));
        s.add(new StringRule("bat", "executable", "download", true));
        s.add(new StringRule("pif", "executable", "download" , true));
        s.add(new StringRule("scr", "executable", "download" , true));
        s.add(new StringRule("cpl", "executable", "download" , true));
        s.add(new StringRule("zip", "archive", "download" , true));
        s.add(new StringRule("hqx", "archive", "download", true));
        s.add(new StringRule("rar", "archive", "download" , true));
        s.add(new StringRule("arj", "archive", "download" , true));
        s.add(new StringRule("ace", "archive", "download" , true));
        s.add(new StringRule("gz", "archive", "download" , true));
        s.add(new StringRule("tar", "archive", "download" , true));
        s.add(new StringRule("jpg", "image", "download", false));
        s.add(new StringRule("png", "image", "download", false ));
        s.add(new StringRule("gif", "image", "download", false));
        s.add(new StringRule("jar", "java", "download", false));
        s.add(new StringRule("class", "java", "download", false));
        s.add(new StringRule("mp3", "audio", "download", false));
        s.add(new StringRule("wav", "audio", "download", false));
        s.add(new StringRule("wmf", "audio", "download", false));
        s.add(new StringRule("mov", "video", "download", false));
        s.add(new StringRule("mpg", "video", "download", false));
        s.add(new StringRule("avi", "video", "download", false));
        s.add(new StringRule("swf", "flash", "download", false));
        s.add(new StringRule("mp3", "audio", "stream", false));
        s.add(new StringRule("wav", "audio", "stream", false));
        s.add(new StringRule("wmf", "audio", "stream", false));
        s.add(new StringRule("mov", "video", "stream", false));
        s.add(new StringRule("mpg", "video", "stream", false));
        s.add(new StringRule("avi", "video", "stream", false));
        vs.setExtensions(s);

        setVirusSettings(vs);
    }

    protected void preInit(String args[])
    {
        Session s = TransformContextFactory.context().openSession();
        try {
            Transaction tx = s.beginTransaction();

            Query q = s.createQuery
                ("from VirusSettings vs where vs.tid = :tid");
            q.setParameter("tid", getTid());
            settings = (VirusSettings)q.uniqueResult();

            tx.commit();
        } catch (HibernateException exn) {
            logger.warn("Could not get VirusSettings", exn);
        } finally {
            try {
                s.close();
            } catch (HibernateException exn) {
                logger.warn("could not close Hibernate session", exn);
            }
        }
    }

    protected void preStart()
    {
        virusReconfigure();
    }

    protected void postStart()
    {
        shutdownMatchingSessions();
    }

    // package protected methods ----------------------------------------------

    VirusScanner getScanner()
    {
        return scanner;
    }

    int getTricklePercent()
    {
        return settings.getTricklePercent();
    }

    List getExtensions()
    {
        return settings.getExtensions();
    }

    List getHttpMimeTypes()
    {
        return settings.getHttpMimeTypes();
    }

    boolean getFtpDisableResume()
    {
        return settings.getFtpDisableResume();
    }

    // XXX soon to be deprecated ----------------------------------------------

    public Object getSettings()
    {
        return getVirusSettings();
    }

    public void setSettings(Object settings)
    {
        setVirusSettings((VirusSettings)settings);
    }

    public void reconfigure()
    {
        shutdownMatchingSessions();
    }

    protected SessionMatcher sessionMatcher()
    {
        return VIRUS_SESSION_MATCHER;
    }

    // private methods --------------------------------------------------------

    private List<VirusLog> getEventLogs(String q, List<VirusLog> l,
                                          int limit)
    {

        Session s = TransformContextFactory.context().openSession();
        try {
            Connection c = s.connection();
            PreparedStatement ps = c.prepareStatement(q);
            ps.setString(1, scanner.getVendorName());
            ps.setInt(2, limit);
            long l0 = System.currentTimeMillis();
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                long ts = rs.getTimestamp("create_date").getTime();
                Date createDate = new Date(ts);
                String type = rs.getString("type");
                String location = rs.getString("location");
                boolean infected = rs.getBoolean("infected");
                String action = rs.getString("action");
                String virusName = rs.getString("virus_name");
                String clientAddr = rs.getString("c_client_addr");
                int clientPort = rs.getInt("c_client_port");
                String serverAddr = rs.getString("s_server_addr");
                int serverPort = rs.getInt("s_server_port");
                byte clientIntf = rs.getByte("client_intf");
                byte serverIntf = rs.getByte("server_intf");

                Direction d = Direction.getDirection(clientIntf, serverIntf);

                VirusLog rl = new VirusLog
                    (createDate, type, location, infected, action,
                     virusName, clientAddr, clientPort, serverAddr,
                     serverPort, d);

                l.add(0, rl);
            }
            long l1 = System.currentTimeMillis();
            logger.debug("getActiveXLogs() in: " + (l1 - l0));
        } catch (SQLException exn) {
            logger.warn("could not get events", exn);
        } catch (HibernateException exn) {
            logger.warn("could not get events", exn);
        } finally {
            try {
                s.close();
            } catch (HibernateException exn) {
                logger.warn("could not close Hibernate session", exn);
            }
        }

        return l;
    }
}
