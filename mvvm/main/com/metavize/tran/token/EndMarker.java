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

package com.metavize.tran.token;


/**
 * Marks the end of a set of {@link Chunk}s.
 *
 * @author <a href="mailto:amread@metavize.com">Aaron Read</a>
 * @version 1.0
 */
public class EndMarker extends MetadataToken
{
    public static final EndMarker MARKER = new EndMarker();

    private EndMarker() { }
}
