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
package com.metavize.tran.mail.web.euv.tags;

import javax.servlet.ServletRequest;


/**
 * Includes/excludes body chunks if the
 * current RowsPerPage option is currently active
 */
public final class RPPIsCurrentOptionSelectedTag
  extends IfElseTag {

  private static final String KEY = "metavize.RPPIsCurrentOptionSelectedTag";
  
  @Override
  protected boolean isConditionTrue() {
    String currentOption = RPPCurrentOptionTag.getCurrent(pageContext);
    String currentValue = PagnationPropertiesTag.getCurrentRowsPerPAge(pageContext.getRequest());

    return (currentValue==null || currentOption==null)?
      false:
      currentValue.trim().equals(currentOption.trim());
  }
}
