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


package com.metavize.tran.test.gui;

import com.metavize.gui.transform.*;
import com.metavize.gui.pipeline.MPipelineJPanel;
import com.metavize.mvvm.tran.TransformContext;


public class MTransformDisplayJPanel extends com.metavize.gui.transform.MTransformDisplayJPanel{
    

    
    public MTransformDisplayJPanel(MTransformJPanel mTransformJPanel) {
        super(mTransformJPanel);
        
        super.activity0JLabel.setText("ACT 1");
        super.activity1JLabel.setText("ACT 2");
        super.activity2JLabel.setText("ACT 3");
        super.activity3JLabel.setText("ACT 4");
    }
    
}
