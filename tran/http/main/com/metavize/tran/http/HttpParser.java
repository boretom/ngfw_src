/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.metavize.mvvm.MvvmContextFactory;
import com.metavize.mvvm.tapi.Pipeline;
import com.metavize.mvvm.tapi.TCPSession;
import com.metavize.mvvm.tapi.TCPSessionDesc;
import com.metavize.mvvm.tran.MimeType;
import com.metavize.tran.token.AbstractParser;
import com.metavize.tran.token.Chunk;
import com.metavize.tran.token.EndMarker;
import com.metavize.tran.token.Header;
import com.metavize.tran.token.ParseException;
import com.metavize.tran.token.ParseResult;
import com.metavize.tran.token.Token;
import com.metavize.tran.token.TokenStreamer;
import org.apache.log4j.Logger;

public class HttpParser extends AbstractParser
{
    private static final byte SP = ' ';
    private static final byte HT = '\t';
    private static final byte CR = '\r';
    private static final byte LF = '\n';

    private static final int NO_BODY = 0;
    private static final int CLOSE_ENCODING = 1;
    private static final int CHUNKED_ENCODING = 2;
    private static final int CONTENT_LENGTH_ENCODING = 3;

    // longest allowable token or text:
    private static final int BUFFER_SIZE = 4096;
    private static final int TIMEOUT = 30000;

    private static final int PRE_FIRST_LINE_STATE = 0;
    private static final int FIRST_LINE_STATE = 1;
    private static final int ACCUMULATE_HEADER_STATE = 2;
    private static final int HEADER_STATE = 3;
    private static final int CLOSED_BODY_STATE = 4;
    private static final int CONTENT_LENGTH_BODY_STATE = 5;
    private static final int CHUNK_LENGTH_STATE = 6;
    private static final int CHUNK_BODY_STATE  = 7;
    private static final int CHUNK_END_STATE  = 8;
    private static final int LAST_CHUNK_STATE = 9;
    private static final int END_MARKER_STATE = 10;

    private final HttpCasing casing;
    private final byte[] buf = new byte[BUFFER_SIZE];
    private final String sessStr;

    private final Logger logger = Logger.getLogger(HttpParser.class);
    private final Logger eventLogger = MvvmContextFactory.context()
        .eventLogger();

    private RequestLine requestLine;
    private StatusLine statusLine;
    private Header header;

    private int state;
    private int transferEncoding;
    private int contentLength; /* counts down content-length and chunks */
    private int lengthCounter; /* counts up to final */

    // constructors -----------------------------------------------------------

    HttpParser(TCPSession session, boolean clientSide, HttpCasing casing)
    {
        super(session, clientSide);
        this.casing = casing;
        this.sessStr = "HttpParser sid: " + session.id()
            + (clientSide ? " client-side" : " server-side");

        lineBuffering(true);
    }

    // Parser methods ------------------------------------------------------

    public ParseResult parse(ByteBuffer b) throws ParseException
    {
        cancelTimer();

        logger.debug(sessStr + "parsing chunk: " + b);
        List l = new LinkedList();

        boolean done = false;
        while (!done) {
            switch (state) {
            case PRE_FIRST_LINE_STATE:
                {
                    logger.debug(sessStr + "in PRE_FIRST_LINE_STATE");

                    lengthCounter = 0;

                    if (b.hasRemaining() && completeLine(b)) {
                        ByteBuffer d = b.duplicate();
                        byte b1 = d.get();
                        if (LF == b1
                            || d.hasRemaining() && CR == b1 && LF == d.get()) {
                            b = null;
                            done = true;
                        } else {
                            state = FIRST_LINE_STATE;
                        }
                    } else {
                        b.compact();
                        done = true;
                    }

                    break;
                }
            case FIRST_LINE_STATE:
                {
                    logger.debug(sessStr + "in FIRST_LINE_STATE");
                    if (completeLine(b)) {
                        l.add(firstLine(b));
                        state = ACCUMULATE_HEADER_STATE;
                    } else {
                        b.compact();
                        done = true;
                    }
                    break;
                }
            case ACCUMULATE_HEADER_STATE:
                {
                    logger.debug(sessStr + "in ACCUMULATE_HEADER_STATE");
                    if (!completeHeader(b)) {
                        b.compact();
                        if (!b.hasRemaining()) {
                            logger.error(sessStr
                                         + "header can't fit in this buffer");
                        }
                        done = true;
                    } else {
                        state = HEADER_STATE;
                    }
                    break;
                }
            case HEADER_STATE:
                {
                    logger.debug(sessStr + "in HEADER_STATE");
                    header = header(b);
                    l.add(header);

                    assert !b.hasRemaining();

                    if (!clientSide) {
                        HttpMethod method = requestLine.getMethod();
                        logger.debug(sessStr + "handling response: " + method);
                        if (HttpMethod.HEAD == method) {
                            transferEncoding = NO_BODY;
                        }
                    }

                    if (NO_BODY == transferEncoding) {
                        state = END_MARKER_STATE;
                    } else if (CLOSE_ENCODING == transferEncoding) {
                        lineBuffering(false);
                        b = null;
                        state = CLOSED_BODY_STATE;
                        done = true;
                    } else if (CHUNKED_ENCODING == transferEncoding) {
                        lineBuffering(true);
                        b = null;
                        state = CHUNK_LENGTH_STATE;
                        done = true;
                    } else if (CONTENT_LENGTH_ENCODING == transferEncoding) {
                        lineBuffering(false);
                        assert !b.hasRemaining();

                        if (0 < contentLength) {
                            readLimit(contentLength);
                            b = null;
                            state = CONTENT_LENGTH_BODY_STATE;
                            done = true;
                        } else {
                            state = END_MARKER_STATE;
                        }
                    } else {
                        assert false;
                    }
                    break;
                }
            case CLOSED_BODY_STATE:
                {
                    logger.debug(sessStr + "in CLOSED_BODY_STATE!");
                    l.add(closedBody(b));
                    b = null;
                    done = true;
                    break;
                }
            case CONTENT_LENGTH_BODY_STATE:
                {
                    logger.debug(sessStr + "in CONTENT_LENGTH_BODY_STATE");
                    l.add(chunk(b));
                    if (0 == contentLength) {
                        b = null;
                        // XXX handle trailer
                        state = END_MARKER_STATE;
                    } else {
                        readLimit(contentLength);
                        b = null;
                        done = true;
                    }
                    break;
                }
            case CHUNK_LENGTH_STATE:
                // chunk-size     = 1*HEX
                {
                    logger.debug(sessStr + "in CHUNK_LENGTH_STATE");
                    if (!completeLine(b)) {
                        b.compact();
                        done = true;
                        break;
                    }

                    contentLength = chunkLength(b);
                    logger.debug(sessStr + "CHUNK contentLength = "
                                 + contentLength);
                    if (0 == contentLength) {
                        b = null;
                        state = LAST_CHUNK_STATE;
                    } else {
                        lineBuffering(false);
                        assert !b.hasRemaining();

                        readLimit(contentLength);
                        b = null;

                        state = CHUNK_BODY_STATE;
                    }
                    done = true;
                    break;
                }
            case CHUNK_BODY_STATE:
                {
                    logger.debug(sessStr + "in CHUNKED_BODY_STATE");

                    l.add(chunk(b));

                    if (0 == contentLength) {
                        lineBuffering(true);
                        b = null;
                        state = CHUNK_END_STATE;
                    } else {
                        readLimit(contentLength);
                        b = null;
                    }

                    done = true;
                    break;
                }
            case CHUNK_END_STATE:
                {
                    logger.debug(sessStr + "in END_CHUNK_STATE");

                    if (!completeLine(b)) {
                        b.compact();
                        done = true;
                        break;
                    }

                    eatCrLf(b);
                    assert !b.hasRemaining();

                    b = null;
                    done = true;

                    state = CHUNK_LENGTH_STATE;
                    break;
                }
            case LAST_CHUNK_STATE:
                // last-chunk     = 1*("0") [ chunk-extension ] CRLF
                {
                    logger.debug(sessStr + "in LAST_CHUNK_STATE");
                    if (!completeLine(b)) {
                        b.compact();
                        done = true;
                        break;
                    }

                    eatCrLf(b);

                    assert !b.hasRemaining();

                    b = null;

                    state = END_MARKER_STATE;
                    break;
                }
            case END_MARKER_STATE:
                {
                    logger.debug(sessStr + "in END_MARKER_STATE");
                    EndMarker endMarker = EndMarker.MARKER;
                    l.add(endMarker);
                    lineBuffering(true);
                    b = null;
                    state = PRE_FIRST_LINE_STATE;

                    if (!clientSide) {
                        String contentType = header.getValue("content-type");
                        String mimeType = null == contentType ? null
                            : MimeType.getType(contentType);

                        HttpResponseEvent evt = new HttpResponseEvent
                            (requestLine, mimeType, lengthCounter);
                        eventLogger.info(evt);
                    } else {
                        HttpRequestEvent evt = new HttpRequestEvent
                            (session.id(), requestLine,
                             header.getValue("host"), lengthCounter);
                        eventLogger.info(evt);
                    }

                    done = true;
                    break;
                }
            default:
                assert false;
            }
        }

        logger.debug(sessStr + "returing readBuffer: " + b);

        scheduleTimer(TIMEOUT);
        return new ParseResult((Token[])l.toArray(new Token[l.size()]), b);
    }

    public ParseResult parseEnd(ByteBuffer buf) throws ParseException
    {
        if (buf.hasRemaining()) {
            logger.warn("data trapped in read buffer: " + buf.remaining());
        }

        // we should implement this to make sure end markers get sent always

        return new ParseResult(null, null);
    }

    public TokenStreamer endSession()
    {
        if (state != PRE_FIRST_LINE_STATE) {
            Pipeline pipeline = MvvmContextFactory.context().pipelineFoundry()
                .getPipeline(session.id());

            return new TokenStreamer(pipeline)
                {
                    private boolean sent = false;

                    public boolean closeWhenDone() { return true; }

                    protected Token nextToken()
                    {
                        if (sent) {
                            return null;
                        } else {
                            sent = true;
                            return EndMarker.MARKER;
                        }
                    }
                };
        } else {
            return null;
        }
    }

    public void handleTimer()
    {
        byte cs = session.clientState();
        byte ss = session.serverState();

        logger.debug(sessStr + " handling timer cs=" + cs + " ss=" + ss);

        if (cs == TCPSessionDesc.HALF_OPEN_OUTPUT
            && ss == TCPSessionDesc.HALF_OPEN_INPUT) {
            logger.debug(sessStr + "closing session because its in halfstate");
            session.shutdownClient();
        } else {
            scheduleTimer(TIMEOUT);
        }
    }

    // private methods --------------------------------------------------------

    private boolean completeLine(ByteBuffer b)
    {
        return b.get(b.limit() - 1) == LF;
    }

    private boolean completeHeader(ByteBuffer b)
    {
        ByteBuffer d = b.duplicate();

        if (d.remaining() >= 4) {
            d.position(d.limit() - 4);
        }

        byte c = ' ';
        while (CR != c && LF != c) {
            if (d.hasRemaining()) {
                c = d.get();
            } else {
                return false;
            }
        }

        if (LF == c || CR == c && d.hasRemaining() && LF == d.get()) {
            if (d.hasRemaining()) {
                c = d.get();
                return LF == c || CR == c && d.hasRemaining() && LF == d.get();
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private Object firstLine(ByteBuffer data) throws ParseException
    {
        if (!clientSide) {
            requestLine = casing.dequeueRequest();
            return statusLine = statusLine(data);
        } else {
            return requestLine = requestLine(data);
        }
    }

    // Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
    private RequestLine requestLine(ByteBuffer data) throws ParseException
    {
        transferEncoding = NO_BODY;

        HttpMethod method = HttpMethod.getInstance(token(data));
        logger.debug(sessStr + "method: " + method);
        eat(data, SP);
        URI requestUri = requestUri(data);
        eat(data, SP);
        String httpVersion = version(data);
        eatCrLf(data);

        return new RequestLine(method, requestUri, httpVersion);
    }

    // Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
    private StatusLine statusLine(ByteBuffer data) throws ParseException
    {
        transferEncoding = CLOSE_ENCODING;

        String httpVersion = version(data);
        eat(data, SP);
        int statusCode = statusCode(data);
        eat(data, SP);
        String reasonPhrase = reasonPhrase(data);
        eatCrLf(data);

        // 4.4 Message Length
        // 1. Any response message which "MUST NOT" include a
        // message-body (such as the 1xx, 204, and 304 responses and
        // any response to a HEAD request) is always terminated by the
        // first empty line after the header fields, regardless of the
        // entity-header fields present in the message.
        if (100 <= statusCode && 199 >= statusCode
            || 204 == statusCode || 304 == statusCode) {
            transferEncoding = NO_BODY;
        }

        return new StatusLine(httpVersion, statusCode, reasonPhrase);
    }

    // HTTP-Version   = "HTTP" "/" 1*DIGIT "." 1*DIGIT
    private String version(ByteBuffer data) throws ParseException
    {
        eat(data, "HTTP");
        eat(data, '/');
        int maj = eatDigits(data);
        eat(data, '.');
        int min = eatDigits(data);

        return "HTTP/" + maj + "." + min;
    }

    // Reason-Phrase  = *<TEXT, excluding CR, LF>
    private String reasonPhrase(ByteBuffer b) throws ParseException
    {
        int l = b.remaining();

        for (int i = 0; b.hasRemaining(); i++) {
            if (isCtl(buf[i] = b.get())) {
                b.position(b.position() - 1);
                return new String(buf, 0, i);
            }
        }

        return new String(buf, 0, l);
    }

    // Status-Code    =
    //       "100"  ; Section 10.1.1: Continue
    //     | ...
    //     | extension-code
    // extension-code = 3DIGIT
    private int statusCode(ByteBuffer b) throws ParseException
    {
        int i = eatDigits(b);

        if (1000 < i || 100 > i) {
            // assumes no status codes begin with 0
            throw new ParseException("expected 3*DIGIT, got: " + i);
        }

        return i;
    }

    private Header header(ByteBuffer data) throws ParseException
    {
        Header header = new Header();

        while (data.remaining() > 2) {
            field(header, data);
            eatCrLf(data);
        }

        while (data.hasRemaining()) {
            eatCrLf(data);
        }

        return header;
    }

    // message-header = field-name ":" [ field-value ]
    // field-name     = token
    // field-value    = *( field-content | LWS )
    // field-content  = <the OCTETs making up the field-value
    //                  and consisting of either *TEXT or combinations
    //                  of token, separators, and quoted-string>
    private void field(Header header, ByteBuffer data)
        throws ParseException
    {
        String key = token(data).trim();
        eat(data, ':');
        String value = eatText(data).trim();

        logger.debug(sessStr + "field key: " + key + " value: " + value);

        // 4.3: The presence of a message-body in a request is signaled by the
        // inclusion of a Content-Length or Transfer-Encoding header field in
        // the request's message-headers.
        // XXX check for valid body in the *reply* as well!
        if (key.equalsIgnoreCase("transfer-encoding")) {
            if (value.equalsIgnoreCase("chunked")) {
                logger.debug(sessStr + "using chunked encoding");
                transferEncoding = CHUNKED_ENCODING;
            } else {
                logger.warn("don't know transfer-encoding: " + value);
            }
        } else if (key.equalsIgnoreCase("content-length")) {
            logger.debug(sessStr + "using content length encoding");
            transferEncoding = CONTENT_LENGTH_ENCODING;
            contentLength = Integer.parseInt(value);
            logger.debug(sessStr + "CL contentLength = " + contentLength);
        } else if (key.equalsIgnoreCase("accept-encoding")) {
            //value = "identity";
        }

        header.addField(key, value);
    }

    private Chunk closedBody(ByteBuffer buffer) throws ParseException
    {
        lengthCounter += buffer.remaining();
        return new Chunk(buffer.slice());
    }

    private int chunkLength(ByteBuffer b) throws ParseException
    {
        int i = 0;

        while (b.hasRemaining()) {
            byte c = b.get();
            if (isHex(c)) {
                i = 16 * i + hexValue((char)c);
            } else if (';' == c) {
                // XXX
                logger.warn(sessStr + "chunk extension not supported yet");
            } else if (CR == c || LF == c) {
                b.position(b.position() - 1);
                break;
            } else if (SP == c) {
                // ignore spaces
            } else {
                // XXX
                logger.warn(sessStr + "unknown character in chunk length: " + c);
            }
        }

        eatCrLf(b);

        return i;
    }

    // chunk          = chunk-size [ chunk-extension ] CRLF
    //                  chunk-data CRLF
    private Chunk chunk(ByteBuffer buffer) throws ParseException
    {
        int remaining = buffer.remaining();
        contentLength -= remaining;
        lengthCounter += remaining;

        assert 0 <= contentLength;

        return new Chunk(buffer.slice());
    }

    // trailer        = *(entity-header CRLF)
    private void handleTrailer(ByteBuffer data) throws ParseException
    {
        header(data);
    }

    // Request-URI    = "*" | absoluteURI | abs_path | authority
    private URI requestUri(ByteBuffer b) throws ParseException
    {
        int l = b.remaining();

        String uri = null;

        for (int i = 0; b.hasRemaining(); i++) {
            buf[i] = b.get();

            if (0 == i && '/' != buf[0]) {
                throw new ParseException("URI not absolute");
            }

            if (SP == buf[i] || HT == buf[i]) {
                b.position(b.position() - 1);
                uri = new String(buf, 0, i);
                break;
            }
        }

        if (null == uri) {
            uri = new String(buf, 0, l);
        }

        try {
            return escapeUri(uri);
        } catch (URISyntaxException exn) {
            throw new ParseException(exn);
        }
    }

    private URI escapeUri(String uri) throws URISyntaxException
    {
        StringBuilder sb = new StringBuilder(uri.length());

        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            switch (c) {
                // unwise
            case '{': sb.append("%7B"); break;
            case '}': sb.append("%7D"); break;
            case '|': sb.append("%7C"); break;
            case '\\': sb.append("%5C"); break;
            case '^': sb.append("%5E"); break;
            case '[': sb.append("%5B"); break;
            case ']': sb.append("%5D"); break;
            case '`': sb.append("%60"); break;
                // delimiter (except #)
            case '<': sb.append("%3C"); break;
            case '>': sb.append("%3E"); break;
            case '"': sb.append("%22"); break;
            case '%':
                if (uri.length() - 1 < i + 2
                    || (!isHex((byte)uri.charAt(i + 1))
                        && !isHex((byte)uri.charAt(i + 2)))) {
                    sb.append("%25");
                } else {
                    sb.append('%');
                }
                break;
            default:
                if (Character.isISOControl(c)) {
                    sb.append('%');
                    String hexStr = Integer.toHexString(c);
                    if (2 > hexStr.length()) {
                        hexStr = "0" + hexStr;
                    }
                    sb.append(hexStr);
                } else {
                    sb.append(c);
                }
                break;
            }
        }

        return new URI(sb.toString());
    }

    private void eat(ByteBuffer data, String s) throws ParseException
    {
        byte[] sb = s.getBytes();
        for (int i = 0; i < sb.length; i++) {
            eat(data, sb[i]);
        }
    }

    private boolean eat(ByteBuffer data, char c)
    {
        return eat(data, (byte)c);
    }

    private boolean eat(ByteBuffer data, byte c)
    {
        if (!data.hasRemaining()) {
            return false;
        }

        int b = data.get();
        if (b != c) {
            logger.warn(sessStr + "expected: " + b + " got: " + c);
            data.position(data.position() - 1);
            return false;
        } else {
            return true;
        }
    }

    // read *TEXT, folding LWS
    // TEXT           = <any OCTET except CTLs,
    //                  but including LWS>
    private String eatText(ByteBuffer b)
    {
        eatLws(b);

        int l = b.remaining();

        for (int i = 0; b.hasRemaining(); i++) {
            buf[i] = b.get();
            if (isCtl(buf[i])) {
                b.position(b.position() - 1);
                if (eatLws(b)) {
                    buf[i] = SP;
                } else {
                    byte b1 = b.get(b.position());
                    byte b2 = b.get(b.position() + 1);
                    if (LF == b1 || CR == b1 && LF == b2) {
                        return new String(buf, 0, i);
                    } else {
                        b.get();
                        // XXX make this configurable
                        // microsoft IIS thinks its ok to put CTLs in headers
                    }
                }
            }
        }

        return new String(buf, 0, l);
    }

    // LWS            = [CRLF] 1*( SP | HT )
    private boolean eatLws(ByteBuffer b)
    {
        int s = b.position();

        byte b1 = b.get();
        if (CR == b1 && b.hasRemaining()) {
            if (LF != b.get()) {
                b.position(b.position() - 2);
                return false;
            }
        } else if (LF != b1) {
            b.position(b.position() - 1);
        }

        boolean result = false;
        while (b.hasRemaining()) {
            byte c = b.get();
            if (SP != c && HT != c) {
                b.position(b.position() - 1);
                break;
            } else {
                result = true;
            }
        }

        if (!result) {
            b.position(s);
        }

        return result;
    }

    // CRLF           = CR LF
    // in our implementation, CR is optional
    private void eatCrLf(ByteBuffer b) throws ParseException
    {
        byte b1 = b.get();
        boolean ate = LF == b1 || CR == b1 && LF == b.get();
        if (!ate) {
            throw new ParseException("CRLF expected: " + b1);
        }
    }

    // DIGIT          = <any US-ASCII digit "0".."9">
    // this method reads 1*DIGIT
    private int eatDigits(ByteBuffer b) throws ParseException
    {
        boolean foundOne = false;
        int i = 0;

        while (b.hasRemaining()) {
            if (isDigit(b.get(b.position()))) {
                foundOne = true;
                i = i * 10 + (b.get() - '0');
            } else {
                break;
            }
        }

        if (!foundOne) {
            throw new ParseException("no digits found, next digit: "
                                         + b.get(b.position()));
        }

        return i;
    }

    // token          = 1*<any CHAR except CTLs or separators>
    private String token(ByteBuffer b)
    {
        int l = b.remaining();

        for (int i = 0; b.hasRemaining(); i++) {
            buf[i] = b.get();
            if (isCtl(buf[i]) || isSeparator(buf[i])) {
                b.position(b.position() - 1);
                return new String(buf, 0, i);
            }
        }

        return new String(buf, 0, l);
    }

    // separators     = "(" | ")" | "<" | ">" | "@"
    //                | "," | ";" | ":" | "\" | <">
    //                | "/" | "[" | "]" | "?" | "="
    //                | "{" | "}" | SP | HT
    private boolean isSeparator(byte b)
    {
        switch (b) {
        case '(': case ')': case '<': case '>': case '@':
        case ',': case ';': case ':': case '\\': case '"':
        case '/': case '[': case ']': case '?': case '=':
        case '{': case '}': case SP: case HT:
            return true;
        default:
            return false;
        }
    }

    // DIGIT          = <any US-ASCII digit "0".."9">
    private boolean isDigit(byte b)
    {
        return '0' <= b && '9' >= b;
    }

    private boolean isHex(byte b)
    {
        switch (b) {
        case '0': case '1': case '2': case '3': case '4': case '5':
        case '6': case '7': case '8': case '9': case 'a': case 'b':
        case 'c': case 'd': case 'e': case 'f': case 'A': case 'B':
        case 'C': case 'D': case 'E': case 'F':
            return true;
        default:
            return false;
        }
    }

    private int hexValue(char c)
    {
        switch (c) {
        case '0': return 0;
        case '1': return 1;
        case '2': return 2;
        case '3': return 3;
        case '4': return 4;
        case '5': return 5;
        case '6': return 6;
        case '7': return 7;
        case '8': return 8;
        case '9': return 9;
        case 'A': case 'a': return 10;
        case 'B': case 'b': return 11;
        case 'C': case 'c': return 12;
        case 'D': case 'd': return 13;
        case 'E': case 'e': return 14;
        case 'F': case 'f': return 15;
        default: throw new IllegalArgumentException("expects hex digit");
        }
    }

    // CTL            = <any US-ASCII control character
    //                  (octets 0 - 31) and DEL (127)>
    private boolean isCtl(byte b)
    {
        return 0 <= b && 31 >= b || 127 == b;
    }

    private boolean isUpAlpha(byte b)
    {
        return 'A' <= b && 'Z' >= b;
    }

    private boolean isLoAlpha(byte b)
    {
        return 'a' <= b && 'z' >= b;
    }

    private boolean isAlpha(byte b)
    {
        return isUpAlpha(b) || isLoAlpha(b);
    }
}
