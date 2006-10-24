/*
 * Copyright (c) 2003-2006 Untangle Networks, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle Networks, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.tran.token;

import com.metavize.mvvm.tapi.Pipeline;

class ReleaseTokenStreamer implements TokenStreamer
{
    private final TokenStreamer streamer;
    private final Release release;

    private boolean released = false;

    ReleaseTokenStreamer(TokenStreamer streamer, Release release)
    {
        this.streamer = streamer;
        this.release = release;
    }

    // IPStreamer methods -----------------------------------------------------

    public boolean closeWhenDone()
    {
        return true;
    }

    // TokenStreamer methods --------------------------------------------------

    public Token nextToken()
    {
        if (released) {
            return null;
        } else if (null == streamer) {
            released = true;
            return release;
        } else {
            Token t = streamer.nextToken();
            if (null == t) {
                released = true;
                return release;
            } else {
                return t;
            }
        }
    }
}

