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
package com.metavize.tran.kav;

import com.metavize.tran.virus.VirusScanner;
import com.metavize.tran.virus.VirusScannerResult;

public class KavTest
{
    public static void main(String args[])
    {
        if (args.length < 1) {
            System.err.println("Usage: java KavTest <filename>");
            System.exit(1);
        }

        KavScanner scanner = new KavScanner();
        VirusScannerResult result = null;
        
        result = scanner.scanFile(args[0]);

        System.out.println(result);
        System.exit(0);
    }

}
