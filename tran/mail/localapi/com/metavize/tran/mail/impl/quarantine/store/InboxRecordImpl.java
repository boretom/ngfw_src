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

package com.metavize.tran.mail.impl.quarantine.store;

import com.metavize.tran.mail.papi.quarantine.InboxRecord;
import com.metavize.tran.mail.papi.quarantine.MailSummary;
import java.io.Serializable;

/**
 * Private implementation of an Inbox record
 */
public final class InboxRecordImpl
  extends InboxRecord
  implements Serializable {

  public InboxRecordImpl() {}

  public InboxRecordImpl(String mailID,
    long addedOn,
    MailSummary summary,
    String[] recipients) {
    
    super(mailID, addedOn, summary, recipients);
    
  }
}
