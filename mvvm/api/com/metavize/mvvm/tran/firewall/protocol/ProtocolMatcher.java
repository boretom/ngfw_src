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

package com.metavize.mvvm.tran.firewall.protocol;

import com.metavize.mvvm.tapi.Protocol;

public interface ProtocolMatcher
{
    public boolean isMatch( Protocol protocol );

    // This version useful for applying to IPSessionDesc
    public boolean isMatch( short protocol );

    public String toDatabaseString();
}
