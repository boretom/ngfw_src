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

package com.metavize.mvvm.engine;

import com.metavize.mvvm.IntfEnum;

import com.metavize.mvvm.api.RemoteIntfManager;
import com.metavize.mvvm.localapi.LocalIntfManager;

/** Passthru class for access to the api function inside of the interface manager */
public class RemoteIntfManagerImpl implements  RemoteIntfManager
{
    private final LocalIntfManager localIntfManager;

    RemoteIntfManagerImpl( LocalIntfManager lim )
    {
        this.localIntfManager = lim;
    }

    /* Retrieve the current interface enumeration */
    public IntfEnum getIntfEnum()
    {
        return this.localIntfManager.getIntfEnum();
    }
}