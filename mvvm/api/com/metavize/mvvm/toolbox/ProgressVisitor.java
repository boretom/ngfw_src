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

package com.metavize.mvvm.toolbox;

public interface ProgressVisitor
{
    public void visitDownloadSummary(DownloadSummary ds);
    public void visitDownloadProgress(DownloadProgress dp);
    public void visitDownloadComplete(DownloadComplete dc);
    public void visitInstallComplete(InstallComplete ic);
    public void visitInstallTimeout(InstallTimeout it);
}
