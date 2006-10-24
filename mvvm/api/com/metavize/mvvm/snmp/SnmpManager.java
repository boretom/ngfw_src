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

package com.metavize.mvvm.snmp;

/**
 * Interface for the singleton which
 * controls Snmp functionality.
 *
 */
public interface SnmpManager {

  SnmpSettings getSnmpSettings();

  void setSnmpSettings(SnmpSettings settings);

}
