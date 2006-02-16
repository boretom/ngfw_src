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

package com.metavize.tran.nat;

import com.metavize.mvvm.tran.HostName;
import com.metavize.mvvm.tran.IPaddr;

public interface NatBasicSettings
{
    /** Get whether or not nat is enabled. */
    public boolean getNatEnabled();

    public void setNatEnabled( boolean newValue );

    /** Get the base of the internal address. */
    public IPaddr getNatInternalAddress();

    public void setNatInternalAddress( IPaddr newValue );

    /** Get the subnet of the internal addresses. */
    public IPaddr getNatInternalSubnet();

    public void setNatInternalSubnet( IPaddr newValue );

    /**  Get whether or not DMZ is being used. */
    public boolean getDmzEnabled();

    public void setDmzEnabled( boolean newValue );

    /** Get whether or not DMZ events should be logged.*/
    public boolean getDmzLoggingEnabled();

    public void setDmzLoggingEnabled( boolean newValue );

    /** Get the address of the dmz host */
    public IPaddr getDmzAddress();

    public void setDmzAddress( IPaddr newValue );
}