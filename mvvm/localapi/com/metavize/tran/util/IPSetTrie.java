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
package com.metavize.tran.util;

import com.metavize.mvvm.tran.IPMaddr;

public class IPSetTrie implements IPSet {

    private IPSetTrieNode _root = null;

    public IPSetTrie()
    {
        _root = new IPSetTrieNode();
    }

    public void add (IPMaddr mask, Object result)
    {
        _root.add(mask.bitString(),result);
    }

    public Object getMostSpecific  (IPMaddr mask)
    {
        return _root.getMostSpecific(mask.bitString(),0);
    }

    public Object getLeastSpecific (IPMaddr mask)
    {
        return _root.getLeastSpecific(mask.bitString(),0);
    }

}


