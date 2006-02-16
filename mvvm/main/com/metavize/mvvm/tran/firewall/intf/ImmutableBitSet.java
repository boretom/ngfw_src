/*
 * Copyright (c) 2004, 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 *  $Id$
 */

package com.metavize.mvvm.tran.firewall.intf;

import java.util.BitSet;

final class ImmutableBitSet
{
    private final BitSet bitSet;
    
    ImmutableBitSet( BitSet bitSet )
    {
        this.bitSet = bitSet;
    }
    
    boolean get( byte value )
    {
        return bitSet.get((int)value );
    }

    public int hashCode()
    {
        return bitSet.hashCode();
    }

    public boolean equals( Object o )
    {
        if ( o == null ) return false;
        
        if (!( o instanceof ImmutableBitSet )) return false;
        
        return bitSet.equals( ((ImmutableBitSet)o).bitSet );
    }
}
