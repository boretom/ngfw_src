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

package com.metavize.tran.util;

import static com.metavize.tran.util.Ascii.*;

import java.nio.ByteBuffer;

public class BufferUtil
{
    public static boolean endsWithCrLf(ByteBuffer buf)
    {
        return 2 <= buf.remaining()
            && CR == buf.get(buf.limit() - 2)
            && LF == buf.get(buf.limit() - 1);
    }

    /**
     * Find CRLF, starting from position.
     *
     * @param buf buffer to search.
     * @return the absolute index of start of CRLF, or -1 if not
     * found.
     */
    public static int findCrLf(ByteBuffer buf)
    {
        for (int i = buf.position(); i < buf.limit() - 1; i++) {
            if (CR == buf.get(i) && LF == buf.get(i + 1)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Test if buf starts with a string.
     *
     * @param buf ByteBuffer to test.
     * @param s String to match.
     * @return true if the ByteBuffer starts with the String, false
     * otherwise.
     */
    public static boolean startsWith(ByteBuffer buf, String s)
    {
        if (buf.remaining() < s.length()) {
            return false;
        }

        int pos = buf.position();

        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != buf.get(pos + i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Find a string in a buffer.
     *
     * @param buf buffer to search.
     * @param str string to match.
     * @return the absolute index, or -1 if not found.
     */
    public static int findString(ByteBuffer buf, String str)
    {
        ByteBuffer dup = buf.duplicate();

        while (str.length() <= dup.remaining()) {
            if (startsWith(dup, str)) {
                return dup.position();
            } else {
                dup.get();
            }
        }

        return -1;
    }

    public static int findLastString(ByteBuffer buf, String str)
    {
        ByteBuffer dup = buf.duplicate();

        for (int i = buf.limit() - str.length(); buf.position() <= i; i--) {
            dup.position(i);
            if (startsWith(dup, str)) {
                return i;
            }
        }

        return -1;
    }
}
