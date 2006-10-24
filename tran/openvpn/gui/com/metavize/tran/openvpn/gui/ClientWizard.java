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

package com.metavize.tran.openvpn.gui;

import com.metavize.gui.widgets.wizard.*;
import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.util.*;
import com.metavize.gui.transform.*;

import com.metavize.tran.openvpn.*;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Dialog;
import java.awt.Window;

public class ClientWizard extends MWizardJDialog {
    
    private static final String MESSAGE_DIALOG_TITLE = "Setup Wizard Warning";
    private static final String MESSAGE_CLIENT_NOT_CONFIGURED = "You have not finished configuring OpenVPN.  Please run the Setup Wizard again.";

    private MTransformControlsJPanel mTransformControlsJPanel;

    public static ClientWizard factory(Window topLevelWindow, VpnTransform vpnTransform,
				       MTransformControlsJPanel mTransformControlsJPanel) {
	if( topLevelWindow instanceof Frame )
	    return new ClientWizard((Frame)topLevelWindow, vpnTransform, mTransformControlsJPanel);
	else if( topLevelWindow instanceof Dialog )
	    return new ClientWizard((Dialog)topLevelWindow, vpnTransform, mTransformControlsJPanel);
	else
	    return null;
    }

    public ClientWizard(Frame topLevelFrame, VpnTransform vpnTransform, MTransformControlsJPanel mTransformControlsJPanel) {
        super(topLevelFrame, true);
	init(mTransformControlsJPanel, vpnTransform);
    }

    public ClientWizard(Dialog topLevelDialog, VpnTransform vpnTransform, MTransformControlsJPanel mTransformControlsJPanel) {
        super(topLevelDialog, true);
	init(mTransformControlsJPanel, vpnTransform);
    }
    
    private void init(MTransformControlsJPanel mTransformControlsJPanel, VpnTransform vpnTransform){
	this.mTransformControlsJPanel = mTransformControlsJPanel;
        setTitle("Untangle Networks OpenVPN Client Setup Wizard");
        addWizardPageJPanel(new ClientWizardWelcomeJPanel(vpnTransform), "1. Welcome", false, true);
        addWizardPageJPanel(new ClientWizardServerJPanel(vpnTransform), "2. Download Configuration", false, true);
        addWizardPageJPanel(new ClientWizardCongratulationsJPanel(vpnTransform), "3. Congratulations", false, true);
    }

    protected void wizardFinishedAbnormal(int currentPage){
	if( currentPage <= 1 ){
	    MOneButtonJDialog.factory(this, "", MESSAGE_CLIENT_NOT_CONFIGURED, MESSAGE_DIALOG_TITLE, "");
	    super.wizardFinishedAbnormal(currentPage);
	}
	else
	    this.wizardFinishedNormal();
    }

    protected void wizardFinishedNormal(){
	super.wizardFinishedNormal();
	mTransformControlsJPanel.getInfiniteProgressJComponent().startLater("Reconfiguring...");
	mTransformControlsJPanel.getInfiniteProgressJComponent().stopLater(3000l);
	mTransformControlsJPanel.refreshGui();
    }  
        
}


