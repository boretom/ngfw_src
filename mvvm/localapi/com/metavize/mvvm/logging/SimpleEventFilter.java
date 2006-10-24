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

package com.metavize.mvvm.logging;

public interface SimpleEventFilter<E extends LogEvent>
{
    RepositoryDesc getRepositoryDesc();
    String[] getQueries();
    boolean accept(E e);
}
