/*
 * Copyright (c) 2004, 2005, 2006 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.gui.main;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.*;
import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.swing.*;
import javax.swing.border.*;

import com.metavize.gui.configuration.*;
import com.metavize.gui.pipeline.*;
import com.metavize.gui.store.*;
import com.metavize.gui.transform.*;
import com.metavize.gui.upgrade.*;
import com.metavize.gui.util.*;
import com.metavize.gui.widgets.dialogs.*;
import com.metavize.gui.widgets.separator.Separator;
import com.metavize.mvvm.*;
import com.metavize.mvvm.client.*;
import com.metavize.mvvm.policy.*;
import com.metavize.mvvm.security.*;
import com.metavize.mvvm.toolbox.*;
import com.metavize.mvvm.tran.*;

public class PolicyStateMachine implements ActionListener {

    // MVVM DATA MODELS (USED ONLY DURING INIT) //////
    private Map<String,MackageDesc> installedMackageMap;
    private Map<Policy,List<Tid>> policyTidMap;
    private Map<Policy,Map<String,Object>> policyNameMap;
    private List<Tid> serviceTidList;
    private Map<String,Object> serviceNameMap;
    // GUI DATA MODELS /////////
    private Map<ButtonKey,MTransformJButton> storeMap;
    private Map<Policy,Map<ButtonKey,MTransformJButton>> policyToolboxMap;
    private Map<Policy,Map<ButtonKey,MTransformJPanel>> policyRackMap;
    public  Map<Policy,Map<ButtonKey,MTransformJPanel>> getPolicyRackMap(){ return policyRackMap; }
    private Map<ButtonKey,MTransformJButton> serviceToolboxMap;
    private Map<ButtonKey,MTransformJPanel> serviceRackMap;
    // GUI VIEW MODELS //////////
    private JPanel storeJPanel;
    private JScrollPane toolboxJScrollPane;
    private JPanel policyToolboxSocketJPanel;
    private JScrollPane rackJScrollPane;
    private JPanel rackViewJPanel;
    private Map<Policy,JPanel> policyToolboxJPanelMap;
    private Map<Policy,JPanel> policyRackJPanelMap;
    private JPanel serviceToolboxSocketJPanel;
    private JPanel serviceToolboxJPanel;
    private JPanel serviceRackJPanel;
    // MISC REFERENCES ////////
    private JButton policyManagerJButton;
    private JTabbedPane actionJTabbedPane;
    private JComboBox viewSelector;
    private Policy selectedPolicy;
    private JPanel selectedRackJPanel;
    private int lastToolboxScrollPosition = -1;
    private Map<Policy,Integer> lastRackScrollPosition;
    private volatile static int applianceLoadProgress;
    // THREAD QUEUES & THREADS /////////
    BlockingQueue<MTransformJButton> purchaseBlockingQueue;
    StoreModelThread storeModelThread;
    // CONSTANTS /////////////
    private GridBagConstraints buttonGridBagConstraints;
    private GridBagConstraints storeProgressGridBagConstraints;
    private GridBagConstraints storeSpacerGridBagConstraints;
    private GridBagConstraints applianceGridBagConstraints;
    private GridBagConstraints rackGridBagConstraints;
    private GridBagConstraints rackSeparatorGridBagConstraints;
    private GridBagConstraints serviceGridBagConstraints;
    private GridBagConstraints serviceSeparatorGridBagConstraints;
    private Separator serviceSeparator;
    private Separator rackSeparator;
    private static final String POLICY_MANAGER_SEPARATOR = "____________";
    private static final String POLICY_MANAGER_OPTION = "Show Policy Manager";
    private static final int CONCURRENT_LOAD_MAX = 1;
    private static Semaphore loadSemaphore;
    // STORE DELAYS //////////////////
    private static final long STORE_UPDATE_CHECK_SLEEP = 24l*60l*60l*1000l;
    // DOWNLOAD DELAYS ///////////////
    private static final int DOWNLOAD_CHECK_SLEEP_MILLIS = 500;
    private static final int DOWNLOAD_FINAL_PAUSE_MILLIS = 1000;
    // INSTALL DELAYS ////////////////
    private static final int INSTALL_CHECK_SLEEP_MILLIS = 500;
    private static final int INSTALL_FINAL_PAUSE_MILLIS = 1000;
    private static final int INSTALL_CHECK_TIMEOUT_MILLIS = 3*60*1000; // (3 minutes)

    public PolicyStateMachine(JTabbedPane actionJTabbedPane, JPanel rackViewJPanel,
                              JScrollPane toolboxJScrollPane, JPanel policyToolboxSocketJPanel, JPanel serviceToolboxSocketJPanel,
                              JPanel storeJPanel, JButton policyManagerJButton, JScrollPane rackJScrollPane) {
        // MVVM DATA MODELS
        installedMackageMap = new HashMap<String,MackageDesc>();
        policyTidMap = new LinkedHashMap<Policy,List<Tid>>(); // Linked so view selector order is consistent (initially)
        policyNameMap = new HashMap<Policy,Map<String,Object>>();
        serviceTidList = new Vector<Tid>();
        serviceNameMap = new HashMap<String,Object>();
        // GUI DATA MODELS
        storeMap = new TreeMap<ButtonKey,MTransformJButton>();
        policyToolboxMap = new HashMap<Policy,Map<ButtonKey,MTransformJButton>>();
        policyRackMap = new HashMap<Policy,Map<ButtonKey,MTransformJPanel>>();
        serviceToolboxMap = new TreeMap<ButtonKey,MTransformJButton>();
        serviceRackMap = new TreeMap<ButtonKey,MTransformJPanel>();
        // GUI VIEW MODELS
        this.rackViewJPanel = rackViewJPanel;
        this.toolboxJScrollPane = toolboxJScrollPane;
        this.policyToolboxSocketJPanel = policyToolboxSocketJPanel;
        this.serviceToolboxSocketJPanel = serviceToolboxSocketJPanel;
        this.storeJPanel = storeJPanel;
        this.policyManagerJButton = policyManagerJButton;
        this.rackJScrollPane = rackJScrollPane;
        policyToolboxJPanelMap = new HashMap<Policy,JPanel>();
        policyRackJPanelMap = new HashMap<Policy,JPanel>();
        serviceToolboxJPanel = new JPanel();
        serviceRackJPanel = new JPanel();
        serviceToolboxJPanel.setOpaque(false);
        serviceRackJPanel.setOpaque(false);
        serviceToolboxJPanel.setLayout(new GridBagLayout());
        serviceRackJPanel.setLayout(new GridBagLayout());
	// SEPARATORS
	serviceSeparator = new Separator(false);
        serviceSeparator.setForegroundText("Networking");
        rackSeparator = new Separator(true);
	rackSeparator.setForegroundText("Security");
        // MISC REFERENCES
        this.actionJTabbedPane = actionJTabbedPane;
        this.viewSelector = rackSeparator.getJComboBox();
	viewSelector.addActionListener(this);
        lastRackScrollPosition = new HashMap<Policy,Integer>();
        // THREAD QUEUES & THREADS /////////
        purchaseBlockingQueue = new ArrayBlockingQueue<MTransformJButton>(1000);
        new MoveFromStoreToToolboxThread();
        storeModelThread = new StoreModelThread();
        MessageClient msgClient = new MessageClient(Util.getMvvmContext());
        msgClient.setToolboxMessageVisitor(new StoreMessageVisitor());
        msgClient.start();
        // CONSTANTS
        buttonGridBagConstraints = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1d, 0d,
                                                          GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                                          new Insets(0,1,3,3), 0, 0);
        storeProgressGridBagConstraints = new GridBagConstraints(0, 0, 1, 1, 1d, 0d,
                                                                 GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                                                                 new Insets(0,4,0,4), 0, 0);
        storeSpacerGridBagConstraints = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0d, 1d,
                                                               GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                                               new Insets(0,0,0,0), 0, 0);
        applianceGridBagConstraints = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0d, 0d,
                                                             GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                             new Insets(1,0,0,0), 0, 0);
        rackGridBagConstraints = new GridBagConstraints(0, 1, 1, 1, 0d, 0d,
                                                        GridBagConstraints.NORTH, GridBagConstraints.NONE,
                                                        new Insets(0,0,0,12), 0, 0);
        rackSeparatorGridBagConstraints = new GridBagConstraints(0, 0, 1, 1, 0d, 0d,
                                                                 GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                                 new Insets(51,0,0,12), 0, 0);
        serviceGridBagConstraints = new GridBagConstraints(0, 2, 1, 1, 0d, 0d,
                                                           GridBagConstraints.NORTH, GridBagConstraints.NONE,
                                                           new Insets(51,0,0,12), 0, 0);
        serviceSeparatorGridBagConstraints = new GridBagConstraints(0, 2, 1, 1, 0d, 0d,
                                                                    GridBagConstraints.NORTH, GridBagConstraints.NONE,
                                                                    new Insets(1,0,0,12), 0, 0);

        loadSemaphore = new Semaphore(CONCURRENT_LOAD_MAX);
        try{
            // LET THE FUN BEGIN
            initMvvmModel();
            // the order of the following three is based on their output to the progress bar, thats all
            initRackModel(Util.getStatusJProgressBar());
            initToolboxModel(Util.getStatusJProgressBar());
            initViewSelector();
            // CACHING OF CASING CLASSES SO THE PROTOCOL SETTINGS DIALOG LOADS FASTER
            loadAllCasings(false);
        }
        catch(Exception e){
            try{ Util.handleExceptionWithRestart("Error instantiating policy model", e); }
            catch(Exception f){ Util.handleExceptionNoRestart("Error instantiating policy model", f); }
        }

        // CHOOSE A DEFAULT ACTIONBAR POSITION
        if( storeMap.size() > 0 )
            actionJTabbedPane.setSelectedIndex(0);
        else
            actionJTabbedPane.setSelectedIndex(1);

        Util.setPolicyStateMachine(this);
    }

    // HANDLERS ///////////////////////////////////////////
    ///////////////////////////////////////////////////////
    public void actionPerformed(ActionEvent actionEvent){
        if( actionEvent.getSource().equals(viewSelector) ){
            handleViewSelector();
        }
        //else if(actionEvent.getSource().equals(policyManagerJButton)){
        //    handlePolicyManagerJButton();
        //}
    }
    private void handleViewSelector(){
        Policy newPolicy;
        if( viewSelector.getSelectedItem() instanceof String ){
            if( viewSelector.getSelectedItem().equals(POLICY_MANAGER_OPTION) ){
                try{
                    PolicyJDialog policyJDialog = new PolicyJDialog();
                    policyJDialog.setVisible(true);
                    viewSelector.setSelectedItem(selectedPolicy);
                    updatePolicyRacks();
                }
                catch(Exception e){
                    try{ Util.handleExceptionWithRestart("Error handling policy manager action", e); }
                    catch(Exception f){ Util.handleExceptionNoRestart("Error handling policy manager action", f); }
                }
            }
            else
                viewSelector.setSelectedItem(selectedPolicy);
            return;
        }
        else{
            newPolicy = (Policy) viewSelector.getSelectedItem();
            if( newPolicy.equals(selectedPolicy) )
                return;
        }
        // TOOLBOX VIEW AND SCROLL POSITION
        JPanel newPolicyToolboxJPanel = policyToolboxJPanelMap.get(newPolicy);
        int currentToolboxScrollPosition = toolboxJScrollPane.getVerticalScrollBar().getValue();
        policyToolboxSocketJPanel.removeAll();
        policyToolboxSocketJPanel.add( newPolicyToolboxJPanel );
        newPolicyToolboxJPanel.revalidate();
        if( serviceToolboxSocketJPanel.getComponentCount() == 0 ){
            serviceToolboxSocketJPanel.add( serviceToolboxJPanel );
            serviceToolboxJPanel.revalidate();
        }
        toolboxJScrollPane.repaint();
        if( lastToolboxScrollPosition >= 0 )
            toolboxJScrollPane.getVerticalScrollBar().setValue( currentToolboxScrollPosition );
        lastToolboxScrollPosition = currentToolboxScrollPosition;
        // RACK VIEW AND SCROLL POSITION
        lastRackScrollPosition.put(selectedPolicy, rackJScrollPane.getVerticalScrollBar().getValue());
        JPanel newPolicyRackJPanel = policyRackJPanelMap.get(newPolicy);
        if( selectedRackJPanel != null ){ // not the first rack viewed
            rackViewJPanel.remove( selectedRackJPanel );
        }
        else{ // the first rack viewed
            // ADD SERVICES AND SEPARATOR
            rackViewJPanel.add( serviceRackJPanel, serviceGridBagConstraints );
            if( !serviceRackMap.isEmpty() ){
                rackViewJPanel.add( serviceSeparator, serviceSeparatorGridBagConstraints );
            }
            rackViewJPanel.add( rackSeparator, rackSeparatorGridBagConstraints );
            serviceRackJPanel.revalidate();
        }
        // ADD POLICY
        rackViewJPanel.add( newPolicyRackJPanel, rackGridBagConstraints );
        //rackSeparator.setForegroundText( newPolicy.getName() );
        newPolicyRackJPanel.revalidate();
        rackViewJPanel.repaint();
        rackJScrollPane.getVerticalScrollBar().setValue( lastRackScrollPosition.get(newPolicy) );
        selectedRackJPanel = newPolicyRackJPanel;
        selectedPolicy = newPolicy;
    }
    ///////////////////////////////////////////////////////
    // HANDLERS ///////////////////////////////////////////


    // POLICY UPDATING ////////////////////////////////////
    ///////////////////////////////////////////////////////
    private void updatePolicyRacks() throws Exception {
        // BUILD A GUI MODEL AND MVVM MODEL
        Map<Policy,Object> currentPolicyRacks = new HashMap<Policy,Object>();
        Map<Policy,Object> newPolicyRacks = new LinkedHashMap<Policy,Object>();
        for(int i=0; i<((DefaultComboBoxModel)viewSelector.getModel()).getSize()-2; i++) // -2 for the last 2 policy manager options
            currentPolicyRacks.put( (Policy) ((DefaultComboBoxModel)viewSelector.getModel()).getElementAt(i), null );
        for( Policy policy : (List<Policy>) Util.getPolicyManager().getPolicyConfiguration().getPolicies() )
            newPolicyRacks.put( policy, null );
        // FIND THE DIFFERENCES
        Vector<Policy> addedPolicyVector = new Vector<Policy>();
        Vector<Policy> removedPolicyVector = new Vector<Policy>();
        for( Policy newPolicy : newPolicyRacks.keySet() )
            if( !currentPolicyRacks.containsKey(newPolicy) )
                addedPolicyVector.add( newPolicy );
        for( Policy currentPolicy : currentPolicyRacks.keySet() )
            if( !newPolicyRacks.containsKey(currentPolicy) )
                removedPolicyVector.add( currentPolicy );
        // UPDATE VIEW SELECTOR
        Policy activePolicy = (Policy) viewSelector.getSelectedItem();
        DefaultComboBoxModel newModel = new DefaultComboBoxModel();
        for( Policy newPolicy : newPolicyRacks.keySet() ){
            newModel.addElement(newPolicy);
            if( activePolicy.equals(newPolicy) ){
                newModel.setSelectedItem(newPolicy);
                selectedPolicy = newPolicy;
            }
        }
        newModel.addElement(POLICY_MANAGER_SEPARATOR);
        newModel.addElement(POLICY_MANAGER_OPTION);
        if( newModel.getSelectedItem() == null ){
            newModel.setSelectedItem( newModel.getElementAt(0) );
            selectedPolicy = (Policy) newModel.getElementAt(0);
        }
        viewSelector.setModel(newModel);
        // ADD THE NEW AND REMOVE THE OLD
        addedPolicyRacks(addedPolicyVector);
        removedPolicyRacks(removedPolicyVector);
        // UPDATE VIEW
        handleViewSelector();
    }
    private void addedPolicyRacks(final List<Policy> policies){
        Policy firstPolicy = policyToolboxMap.keySet().iterator().next();
        for( Policy policy : policies ){
            // ADD TO GUI DATA MODEL
            policyToolboxMap.put(policy, new TreeMap<ButtonKey,MTransformJButton>());
            policyRackMap.put(policy,new TreeMap<ButtonKey,MTransformJPanel>());
            // ADD TO GUI VIEW MODEL
            JPanel toolboxJPanel = new JPanel();
            toolboxJPanel.setLayout(new GridBagLayout());
            toolboxJPanel.setOpaque(false);
            policyToolboxJPanelMap.put(policy, toolboxJPanel);
            JPanel rackJPanel = new JPanel();
            rackJPanel.setLayout(new GridBagLayout());
            rackJPanel.setOpaque(false);
            policyRackJPanelMap.put(policy, rackJPanel);
            // ADD TO SCROLL POSITION
            lastRackScrollPosition.put(policy,0);
            // POPULATE THE TOOLBOX
            for( Map.Entry<ButtonKey,MTransformJButton> firstPolicyEntry : policyToolboxMap.get(firstPolicy).entrySet() )
                addToToolbox(policy,firstPolicyEntry.getValue().getMackageDesc(),false,false);
            revalidateToolboxes();
        }
    }
    private void removedPolicyRacks(final List<Policy> policies){
        for( Policy policy : policies ){
            // SHUTDOWN ALL APPLIANCES
            /* We dont need to do this anymore since non-empty policies cannot be deleted */
            //for( MTransformJPanel mTransformJPanel : policyRackMap.get(policy).values() ){
            //mTransformJPanel.doShutdown();
            //}
            // REMOVE FROM GUI DATA MODEL
            policyRackMap.get(policy).clear();
            policyRackMap.remove(policy);
            // REMOVE FROM GUI VIEW MODEL
            policyRackJPanelMap.get(policy).removeAll();
            policyRackJPanelMap.remove(policy);
            // REMOVE FROM SCROLL POSITION
            lastRackScrollPosition.remove(policy);
        }
    }
    /////////////////////////////////////////
    // POLICY UPDATING //////////////////////

    // TOOLBOX / STORE OPERATIONS //////////
    ////////////////////////////////////////
    private class MoveFromToolboxToRackThread extends Thread {
        private Policy policy;
        private MTransformJButton mTransformJButton;
        public MoveFromToolboxToRackThread(final Policy policy, final MTransformJButton mTransformJButton){
            setDaemon(true);
            this.policy = policy;
            this.mTransformJButton = mTransformJButton;
            setContextClassLoader( Util.getClassLoader() );
            setName("MVCLIENT-MoveFromToolboxToRackThread: " + mTransformJButton.getDisplayName() + " -> " + (mTransformJButton.getMackageDesc().isService()?"services":policy.getName()));
            mTransformJButton.setDeployingView();
            focusInToolbox(mTransformJButton, false);
            start();
        }
        public void run(){
            try{
                // INSTANTIATE IN MVVM
                Tid tid = Util.getTransformManager().instantiate(mTransformJButton.getName(),policy);
                // CREATE APPLIANCE
                TransformContext transformContext = Util.getTransformManager().transformContext( tid );
                MTransformJPanel mTransformJPanel = MTransformJPanel.instantiate(transformContext);
                // DEPLOY APPLIANCE TO CURRENT POLICY RACK (OR SERVICE RACK)
                addToRack(policy, mTransformJPanel,true);
                // FOCUS AND HIGHLIGHT IN CURRENT RACK
                focusInRack(mTransformJPanel);
                // REMIND USER TO START THAT SUCKER
                mTransformJPanel.setPowerOnHintVisible(true);
            }
            catch(Exception e){
                try{ Util.handleExceptionWithRestart("Error moving from toolbox to rack", e); }
                catch(Exception f){
                    Util.handleExceptionNoRestart("Error moving from toolbox to rack", f);
                    mTransformJButton.setFailedDeployView();
                    new MOneButtonJDialog(mTransformJButton.getDisplayName(),
                                          "A problem occurred while installing to the rack:<br>"
                                          + mTransformJButton.getDisplayName()
                                          + "<br>Please contact Metavize support.");
                    return;
                }
            }
            mTransformJButton.setDeployedView();
            // UPDATE PROTOCOL SETTINGS CACHE
            loadAllCasings(false);
        }
    }
    public void moveFromRackToToolbox(final Policy policy, final MTransformJPanel mTransformJPanel){
        new MoveFromRackToToolboxThread(policy,mTransformJPanel);
    }
    private class MoveFromRackToToolboxThread extends Thread{
        private Policy policy;
        private MTransformJPanel mTransformJPanel;
        private MTransformJButton mTransformJButton;
        private ButtonKey buttonKey;
        public MoveFromRackToToolboxThread(final Policy policy, final MTransformJPanel mTransformJPanel){
            setDaemon(true);
            this.policy = policy;
            this.mTransformJPanel = mTransformJPanel;
            this.buttonKey = new ButtonKey(mTransformJPanel);
            if( mTransformJPanel.getMackageDesc().isService() )
                this.mTransformJButton = serviceToolboxMap.get(buttonKey);
            else
                this.mTransformJButton = policyToolboxMap.get(policy).get(buttonKey);
            setContextClassLoader( Util.getClassLoader() );
            setName("MVCLIENT-MoveFromRackToToolboxThread: " + mTransformJPanel.getMackageDesc().getDisplayName() + " -> " + (mTransformJPanel.getMackageDesc().isService()?"service":policy.getName()));
            mTransformJPanel.setRemovingView(false);
            start();
        }
        public void run(){
            try{
                // DESTROY IN MVVM
                Util.getTransformManager().destroy(mTransformJPanel.getTid());
                // REMOVE APPLIANCE FROM THE CURRENT POLICY RACK
                mTransformJPanel.doShutdown();
                removeFromRack(policy, mTransformJPanel);
            }
            catch(Exception e){
                try{ Util.handleExceptionWithRestart("Error moving from rack to toolbox", e); }
                catch(Exception f){
                    Util.handleExceptionNoRestart("Error moving from rack to toolbox", f);
                    mTransformJPanel.setProblemView(true);
                    mTransformJButton.setFailedRemoveFromRackView();
                    new MOneButtonJDialog(mTransformJPanel.getMackageDesc().getDisplayName(),
                                          "A problem occurred while removing from the rack:<br>"
                                          + mTransformJPanel.getMackageDesc().getDisplayName()
                                          + "<br>Please contact Metavize support.");
                    return;
                }
            }
            // VIEW: DEPLOYABLE
            MTransformJButton targetMTransformJButton;
            if( mTransformJPanel.getMackageDesc().isService() )
                targetMTransformJButton = serviceToolboxMap.get(buttonKey);
            else
                targetMTransformJButton = policyToolboxMap.get(policy).get(buttonKey);
            targetMTransformJButton.setDeployableView();
            focusInToolbox(targetMTransformJButton, true);
        }
    }
    
    private class MoveFromToolboxToStoreThread extends Thread{
	private MTransformJButton mTransformJButton;
	private Vector<MTransformJButton> buttonVector;
	public MoveFromToolboxToStoreThread(final MTransformJButton mTransformJButton){
	    this.mTransformJButton = mTransformJButton;
	    ButtonKey buttonKey = new ButtonKey(mTransformJButton);
	    buttonVector = new Vector<MTransformJButton>();
	    setContextClassLoader( Util.getClassLoader() );
	    setName("MVCLIENT-MoveFromToolboxToStoreThread: " + mTransformJButton.getDisplayName() );
	    // DECIDE IF WE CAN REMOVE
	    if( mTransformJButton.getMackageDesc().isService() ){
		buttonVector.add(mTransformJButton);
	    }
	    else{
		for( Map.Entry<Policy,Map<ButtonKey,MTransformJButton>> policyToolboxMapEntry : policyToolboxMap.entrySet() ){
		    Map<ButtonKey,MTransformJButton> toolboxMap = policyToolboxMapEntry.getValue();
		    if( toolboxMap.containsKey(buttonKey) && toolboxMap.get(buttonKey).isEnabled() ){
			buttonVector.add( toolboxMap.get(buttonKey) );
		    }
		    else{
			new MOneButtonJDialog(mTransformJButton.getDisplayName(),
					      mTransformJButton.getDisplayName()
					      + " cannot be removed from the toolbox because it is being"
					      + " used by the following policy rack:<br><b>"
					      + policyToolboxMapEntry.getKey().getName()
					      + "</b><br><br>You must remove the appliance from all policy racks first.");
			return;
		    }
		}
	    }
	    for( MTransformJButton button : buttonVector )
		button.setRemovingFromToolboxView();
	    start();
	}
	public void run(){
	    try{
		// UNINSTALL IN MVVM
		Util.getToolboxManager().uninstall(mTransformJButton.getName());
		// REMOVE FROM TOOLBOX
		removeFromToolbox(mTransformJButton.getMackageDesc());
		// UPDATE STORE MODEL
		updateStoreModel();
	    }
	    catch(Exception e){
		try{ Util.handleExceptionWithRestart("Error moving from toolbox to store", e); }
		catch(Exception f){
		    Util.handleExceptionNoRestart("Error moving from toolbox to store", f);
		    mTransformJButton.setFailedRemoveFromToolboxView();
		    new MOneButtonJDialog(mTransformJButton.getDisplayName(),
					  "A problem occurred while removing from the toolbox:<br>"
					  + mTransformJButton.getDisplayName()
					  + "<br>Please contact Metavize support.");
		    return;
		}
	    }
	}
    }
    


    private class StoreMessageVisitor implements ToolboxMessageVisitor {
        public void visitMackageInstallRequest(MackageInstallRequest req) {
            String mackageName = req.getMackageName();
            MTransformJButton mTransformJButton = null;
            for( MTransformJButton purchasedButton : storeMap.values() ){
                if(purchasedButton.getName().equals(mackageName)){
                    mTransformJButton = purchasedButton;
                    break;
                }
            }
            try{
                if(mTransformJButton == null)
                    throw new Exception();
                purchaseBlockingQueue.put(mTransformJButton);
            }
            catch (Exception e) {
                Util.handleExceptionNoRestart("Error purchasing", e);
                mTransformJButton.setFailedProcureView();
            }
        }
    }
    /*
    public void moveFromStoreToToolbox(final MTransformJButton mTransformJButton){
        mTransformJButton.setProcuringView();
        try{
            purchaseBlockingQueue.put(mTransformJButton);
        }
        catch(Exception e){
            Util.handleExceptionNoRestart("Interrupted while waiting to purchase", e);
            mTransformJButton.setFailedProcureView();
        }
    }
    */
    private class MoveFromStoreToToolboxThread extends Thread{
        public MoveFromStoreToToolboxThread(){
            setDaemon(true);
            setContextClassLoader( Util.getClassLoader() );
            setName("MVCLIENT-MoveFromStoreToToolboxThread");
            start();
        }
        public void run(){
            while(true){
                MTransformJButton purchasedMTransformJButton;
                try{
                    purchasedMTransformJButton = purchaseBlockingQueue.take();
                    purchase(purchasedMTransformJButton);
                }
                catch(Exception e){
                    Util.handleExceptionNoRestart("Interrupted while waiting to purchase", e);
                }
            }
        }
        private void purchase(final MTransformJButton mTransformJButton){
            try{
                // DO THE DOWNLOAD
                MackageDesc[] originalUninstalledMackages = Util.getToolboxManager().uninstalled();
                MackageDesc[] currentUninstalledMackages = null;
                //// MAKE SURE WE HAVENT ALREADY IMPLICITLY DOWNLOADED THIS PACKAGE AS PART OF A PREVIOUS BUNDLE
                boolean found = false;
                for( MackageDesc mackageDesc : originalUninstalledMackages ){
                    if(mTransformJButton.getName().equals(mackageDesc.getName())){
                        found = true;
                        break;
                    }
                }
                if( !found )
                    return; // BECAUSE THE PACKAGE WAS ALREADY PURCHASED
                MackageDesc[] originalInstalledMackages = Util.getToolboxManager().installed();
                MackageDesc[] currentInstalledMackages = null;
                long key = Util.getToolboxManager().install(mTransformJButton.getName());
                com.metavize.gui.util.Visitor visitor = new com.metavize.gui.util.Visitor(mTransformJButton);
                while (true) {
                    java.util.List<InstallProgress> lip = Util.getToolboxManager().getProgress(key);
                    for (InstallProgress ip : lip) {
                        ip.accept(visitor);
                        if( visitor.isDone() )
                            break;
                    }
                    if( visitor.isDone() )
                        break;
                    if (0 == lip.size()) {
                        Thread.currentThread().sleep(DOWNLOAD_CHECK_SLEEP_MILLIS);
                    }
                }
                if( !visitor.isSuccessful() )
                    throw new Exception();
                Thread.currentThread().sleep(DOWNLOAD_FINAL_PAUSE_MILLIS);

                // DO THE INSTALL
                long installStartTime = System.currentTimeMillis();
                SwingUtilities.invokeAndWait( new Runnable(){ public void run(){
                    mTransformJButton.setProgress("Installing...", 101);
                }});
                boolean mackageInstalled = false;
                while( !mackageInstalled && ((System.currentTimeMillis() - installStartTime) < INSTALL_CHECK_TIMEOUT_MILLIS) ){
                    currentInstalledMackages = Util.getToolboxManager().installed();
                    for( MackageDesc mackageDesc : currentInstalledMackages ){
                        if(mackageDesc.getName().equals(mTransformJButton.getName())){
                            mackageInstalled = true;
                            SwingUtilities.invokeAndWait( new Runnable(){ public void run(){
                                mTransformJButton.setProgress("Success", 100);
                            }});
                            break;
                        }
                    }
                    if( !mackageInstalled )
                        Thread.currentThread().sleep(INSTALL_CHECK_SLEEP_MILLIS);
                }
                if( !mackageInstalled )
                    throw new Exception();
                Thread.currentThread().sleep(INSTALL_FINAL_PAUSE_MILLIS);

                // REMOVE FROM STORE
                currentUninstalledMackages = Util.getToolboxManager().uninstalled();
                List<MackageDesc> purchasedMackageDescs = computeNewMackageDescs(currentUninstalledMackages, originalUninstalledMackages);
                for( MackageDesc purchasedMackageDesc : purchasedMackageDescs ){
                    if( isMackageStoreItem(purchasedMackageDesc) ){
                        System.err.println("PURCHASED: " + purchasedMackageDesc.getName());
                        removeFromStore(purchasedMackageDesc);
                    }
                }
		// BRING MAIN WINDOW TO FRONT
		Util.getMMainJFrame().toFront();
                // ADD TO TOOLBOX
                List<MackageDesc> newMackageDescs = computeNewMackageDescs(originalInstalledMackages, currentInstalledMackages);
                for( MackageDesc newMackageDesc : newMackageDescs ){
                    if( !isMackageStoreItem(newMackageDesc) ){
                        System.err.println("INSTALLED: " + newMackageDesc.getName());
                        MTransformJButton newMTransformJButton = new MTransformJButton(newMackageDesc);
                        if( newMTransformJButton.getMackageDesc().isService() ){
                            addToToolbox(null,newMTransformJButton.getMackageDesc(),false,false);
                        }
                        else{
                            for( Policy policy : policyToolboxMap.keySet() )
                                addToToolbox(policy,newMTransformJButton.getMackageDesc(),false,false);
                        }
                        revalidateToolboxes();
                        // FOCUS AND HIGHLIGHT IN CURRENT TOOLBOX
                        focusInToolbox(newMTransformJButton, true);
                    }
                }
            }
            catch(Exception e){
                try{
                    Util.handleExceptionWithRestart("error purchasing: " +  mTransformJButton.getName(),  e);
                }
                catch(Exception f){
                    Util.handleExceptionNoRestart("Error purchasing:", f);
                    mTransformJButton.setFailedProcureView();
                    SwingUtilities.invokeLater( new Runnable(){ public void run(){
                        new MOneButtonJDialog(mTransformJButton.getDisplayName(),
                                              "A problem occurred while purchasing:<br>"
                                              + mTransformJButton.getDisplayName()
                                              + "<br>Please try again or contact Metavize for assistance.");
                    }});
                }
            }
        }
    }
    private List<MackageDesc> computeNewMackageDescs(MackageDesc[] originalInstalledMackages, MackageDesc[] currentInstalledMackages){
        Vector<MackageDesc> newlyInstalledMackages = new Vector<MackageDesc>();
        Hashtable<String,String> originalInstalledMackagesHashtable = new Hashtable<String,String>();
        for( MackageDesc mackageDesc : originalInstalledMackages )
            originalInstalledMackagesHashtable.put(mackageDesc.getName(), mackageDesc.getName());
        for( MackageDesc mackageDesc : currentInstalledMackages )
            if( !originalInstalledMackagesHashtable.containsKey(mackageDesc.getName()) )
                newlyInstalledMackages.add(mackageDesc);
        return newlyInstalledMackages;
    }
    ///////////////////////////////////////////////////////
    // TOOLBOX / STORE OPERATIONS /////////////////////////


    // INIT API ////////////////////////////////////////////
    ////////////////////////////////////////////////////////
    private void initMvvmModel() throws Exception {
        for( MackageDesc mackageDesc : Util.getToolboxManager().installed() )
            installedMackageMap.put(mackageDesc.getName(),mackageDesc);
        for( Policy policy : Util.getPolicyManager().getPolicies() )
            policyTidMap.put( policy, Util.getTransformManager().transformInstances(policy) );
        serviceTidList = Util.getTransformManager().transformInstances((Policy)null);
        // NAME MAPS FOR QUICK LOOKUP
        for( Policy policy : policyTidMap.keySet() ){
            Map<String,Object> nameMap = new HashMap<String,Object>();
            policyNameMap.put(policy,nameMap);
            for( Tid tid : policyTidMap.get(policy) ){
                nameMap.put(tid.getTransformName(),null);
            }
        }
        for( Tid tid : serviceTidList )
            serviceNameMap.put(tid.getTransformName(),null);
    }

    public void updateStoreModel(){ storeModelThread.updateStoreModel(); }
    private class StoreModelThread extends Thread {
        private JProgressBar storeProgressBar;
        private volatile boolean doUpdate = false;
        public StoreModelThread(){
            setDaemon(true);
            storeProgressBar = new JProgressBar();
            storeProgressBar.setStringPainted(true);
            storeProgressBar.setForeground(new java.awt.Color(68, 91, 255));
            storeProgressBar.setFont(new java.awt.Font("Dialog", 0, 12));
            storeProgressBar.setPreferredSize(new java.awt.Dimension(130, 16));
            storeProgressBar.setMaximumSize(new java.awt.Dimension(130, 16));
            storeProgressBar.setMinimumSize(new java.awt.Dimension(130, 16));
            start();
        }
        public synchronized void updateStoreModel(){
            doUpdate = true;
            notify();
        }
        public void run(){
            // POLL MVVM FOR PURCHASES

            // MAIN STORE EVENT LOOP
            while(true){
                try{
                    initStoreModel();
                    synchronized(this){
                        if( doUpdate )
                            doUpdate = false;
                        else
                            wait(STORE_UPDATE_CHECK_SLEEP);
                    }
                }
                catch(Exception e){
                    Util.handleExceptionNoRestart("Error sleeping store check thread", e);
                }
            }
        }
        private void initStoreModel(){
            // SHOW THE USER WHATS GOING ON
            SwingUtilities.invokeLater( new Runnable(){ public void run(){
                // CLEAR OUT THE STORE
                storeMap.clear();
                storeJPanel.removeAll();
                // CREATE PROGRESS BAR AND ADD IT
                storeProgressBar.setValue(0);
                storeProgressBar.setIndeterminate(true);
                storeProgressBar.setString("Connecting...");
                storeJPanel.add(storeProgressBar, storeProgressGridBagConstraints);
                storeJPanel.revalidate();
            }});
            // CHECK FOR STORE CONNECTIVITY AND AVAILABLE ITEMS
            boolean connectedToStore = false;
            MackageDesc[] storeItemsAvailable = null;
            try{
                Util.getToolboxManager().update();
                storeItemsAvailable = Util.getToolboxManager().uninstalled();
                connectedToStore = true;
            }
            catch(Exception e){
                Util.handleExceptionNoRestart("Error: unable to connect to store",e);
            }
            // SHOW RESULTS
            if( !connectedToStore ){
                // NO CONNECTION
                SwingUtilities.invokeLater( new Runnable(){ public void run(){
                    storeProgressBar.setValue(0);
                    storeProgressBar.setIndeterminate(false);
                    storeProgressBar.setString("No Connection");
                }});
            }
            else{
                if( storeItemsAvailable.length == 0 ){
                    // CONNECTION, BUT NO ITEMS AVAILABLE
                    SwingUtilities.invokeLater( new Runnable(){ public void run(){
                        storeProgressBar.setValue(0);
                        storeProgressBar.setIndeterminate(false);
                        storeProgressBar.setString("No New Items");
                    }});
                }
                else{
                    // CONNECTION, ITEMS AVAILABLE
                    SwingUtilities.invokeLater( new Runnable(){ public void run(){
                        storeJPanel.remove(storeProgressBar);
                        JPanel storeSpacerJPanel = new JPanel();
                        storeSpacerJPanel.setOpaque(false);
                        storeJPanel.add(storeSpacerJPanel, storeSpacerGridBagConstraints, 0);
                        storeJPanel.revalidate();
                        storeJPanel.repaint();
                    }});
                    for( MackageDesc mackageDesc : storeItemsAvailable ){
                        addToStore(mackageDesc,false);
                    }
                    revalidateStore();
                }
            }
        }





    }
    private void initToolboxModel(final JProgressBar progressBar){
        // BUILD THE MODEL
        Map<String,MackageDesc> installedMackageMap = new HashMap<String,MackageDesc>();
        for( MackageDesc mackageDesc : Util.getToolboxManager().installed() )
            installedMackageMap.put(mackageDesc.getName(),mackageDesc);
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            progressBar.setValue(64);
            progressBar.setString("Populating Toolbox...");
        }});
        int progress = 0;
        final float overallFinal = (float) (installedMackageMap.size() * (policyTidMap.size()+1)); // +1 for services
        // APPLIANCES
        for( Policy policy : policyTidMap.keySet() ){
            JPanel toolboxJPanel = new JPanel();
            toolboxJPanel.setLayout(new GridBagLayout());
            toolboxJPanel.setOpaque(false);
            policyToolboxJPanelMap.put(policy, toolboxJPanel);
            policyToolboxMap.put(policy, new TreeMap<ButtonKey,MTransformJButton>());
            for( MackageDesc mackageDesc : installedMackageMap.values() ){
                if( !mackageDesc.isService() ){
                    boolean isDeployed = policyNameMap.get(policy).containsKey(mackageDesc.getName());
                    addToToolbox(policy,mackageDesc,isDeployed,false);
                }
                progress++;
            }
            final float progressFinal = (float) progress;
            SwingUtilities.invokeLater( new Runnable(){ public void run(){
                progressBar.setValue(64 + (int) (32f*progressFinal/overallFinal) );
            }});
        }
        // SERVICES
        for( MackageDesc mackageDesc : installedMackageMap.values() ){
            if( mackageDesc.isService() ){
                boolean isDeployed = serviceNameMap.containsKey(mackageDesc.getName());
                addToToolbox(null,mackageDesc,isDeployed,false);
            }
            progress++;
            final float progressFinal = (float) progress;
            SwingUtilities.invokeLater( new Runnable(){ public void run(){
                progressBar.setValue(64 + (int) (32f*progressFinal/overallFinal) );
            }});
        }
        revalidateToolboxes();
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            progressBar.setValue(96);
        }});
    }
    private void initRackModel(final JProgressBar progressBar){
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            progressBar.setValue(16);
            if( policyTidMap.keySet().size() == 1 )
                progressBar.setString("Populating Rack...");
            else
                progressBar.setString("Populating Racks...");
        }});
        // GENERATE OVERALL COUNT
        int overall = 0;
        for( Policy policy : policyTidMap.keySet() )
            overall += policyTidMap.get(policy).size();
        overall += serviceTidList.size();
        // APPLIANCES
        for( Policy policy : policyTidMap.keySet() ){
            lastRackScrollPosition.put(policy,0);
            JPanel rackJPanel = new JPanel();
            rackJPanel.setLayout(new GridBagLayout());
            rackJPanel.setOpaque(false);
            policyRackJPanelMap.put(policy,rackJPanel);
            policyRackMap.put(policy,new TreeMap<ButtonKey,MTransformJPanel>());
            for( Tid tid : policyTidMap.get(policy) ){
                new LoadApplianceThread(policy,tid,overall,progressBar);
            }
        }
        // SERVICES
        applianceLoadProgress = 0;
        for( Tid tid : serviceTidList ){
            new LoadApplianceThread(null,tid,overall,progressBar);
        }
        try{
            while( applianceLoadProgress < overall ){
                Thread.currentThread().sleep(100);
            }
            revalidateRacks();
        }
        catch(Exception e){ Util.handleExceptionNoRestart("Error sleeping while appliances loading",e); }
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            progressBar.setValue(64);
        }});
    }
    private class LoadApplianceThread extends Thread{
        private Policy policy;
        private Tid tid;
        private int overallProgress;
        private JProgressBar progressBar;
        public LoadApplianceThread(Policy policy, Tid tid, int overallProgress, JProgressBar progressBar){
            setDaemon(true);
            this.policy = policy;
            this.tid = tid;
            this.overallProgress = overallProgress;
            this.progressBar = progressBar;
            setName("MVCLIENT-LoadApplianceThread: " + tid.getId());
            setContextClassLoader( Util.getClassLoader() );
            start();
        }
        public void run(){
	    try{
		loadSemaphore.acquire();
		// GET THE TRANSFORM CONTEXT AND MACKAGE DESC
		TransformContext transformContext = Util.getTransformManager().transformContext( tid );
		MackageDesc mackageDesc = transformContext.getMackageDesc();
		if( isMackageVisible(mackageDesc) ){
		    // CONSTRUCT AND ADD THE APPLIANCE
                    MTransformJPanel mTransformJPanel = MTransformJPanel.instantiate(transformContext);
                    addToRack(policy,mTransformJPanel,false);
                }
	    }
	    catch(Exception e){
		try{ Util.handleExceptionWithRestart("Error instantiating appliance: " + tid, e); }
		catch(Exception f){ Util.handleExceptionNoRestart("Error instantiating appliance: " + tid, f); }
	    }
	    finally{
		loadSemaphore.release();
	    }
            
            final float overallFinal = (float) overallProgress;
            SwingUtilities.invokeLater( new Runnable(){ public void run(){
                PolicyStateMachine.this.applianceLoadProgress++;
                float progressFinal = (float) PolicyStateMachine.this.applianceLoadProgress;
                progressBar.setValue(16 + (int) (48f*progressFinal/overallFinal) );
            }});
        }
    }
    private void initViewSelector(){
        DefaultComboBoxModel newModel = new DefaultComboBoxModel();
        for( Policy policy : policyTidMap.keySet() )
            newModel.addElement(policy);
        newModel.addElement(POLICY_MANAGER_SEPARATOR);
        newModel.addElement(POLICY_MANAGER_OPTION);
        newModel.setSelectedItem( newModel.getElementAt(0) );
        viewSelector.setModel(newModel);
        viewSelector.setRenderer( new PolicyRenderer(viewSelector.getRenderer()) );
        handleViewSelector();
    }
    ////////////////////////////////////////
    // INIT API ////////////////////////////


    // REMOVE API /////////////////////////
    ///////////////////////////////////////
    private void removeFromStore(final MackageDesc mackageDesc){
        final ButtonKey buttonKey = new ButtonKey(mackageDesc);
        SwingUtilities.invokeLater( new Runnable() { public void run() {
            MTransformJButton mTransformJButton = storeMap.get(buttonKey);
            for( ActionListener actionListener : mTransformJButton.getActionListeners() )
                mTransformJButton.removeActionListener(actionListener);
            storeMap.remove(buttonKey);
            storeJPanel.remove(mTransformJButton);
            storeJPanel.revalidate();
        }});
    }
    private void removeFromToolbox(final MackageDesc mackageDesc){
        final ButtonKey buttonKey = new ButtonKey(mackageDesc);
        SwingUtilities.invokeLater( new Runnable() { public void run() {
            if( mackageDesc.isService() ){
                int position = ((TreeMap)serviceToolboxMap).headMap(buttonKey).size();
                MTransformJButton mTransformJButton = serviceToolboxMap.get(buttonKey);
                for( ActionListener actionListener : mTransformJButton.getActionListeners() )
                    mTransformJButton.removeActionListener(actionListener);
                serviceToolboxMap.remove(buttonKey);
                serviceToolboxJPanel.remove(position);
                serviceToolboxJPanel.revalidate();
            }
            else{
                for( Policy policy : policyToolboxMap.keySet() ){
                    Map<ButtonKey,MTransformJButton> toolboxMap = policyToolboxMap.get(policy);
                    int position = ((TreeMap)toolboxMap).headMap(buttonKey).size();
                    MTransformJButton mTransformJButton = toolboxMap.get(buttonKey);
                    for( ActionListener actionListener : mTransformJButton.getActionListeners() )
                        mTransformJButton.removeActionListener(actionListener);
                    toolboxMap.remove(buttonKey);
                    JPanel toolboxJPanel = policyToolboxJPanelMap.get(policy);
                    toolboxJPanel.remove(position);
                    toolboxJPanel.revalidate();
                }
            }
        }});
    }
    private void removeFromRack(final Policy policy, final MTransformJPanel mTransformJPanel){
        final ButtonKey buttonKey = new ButtonKey(mTransformJPanel);
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            if( mTransformJPanel.getMackageDesc().isService() ){
                // REMOVE FROM RACK MODEL
                serviceRackMap.remove(buttonKey);
                // REMOVE FROM RACK VIEW
                serviceRackJPanel.remove(mTransformJPanel);
                serviceRackJPanel.revalidate();
                // DEAL WITH SPACER
                if( serviceRackMap.isEmpty() ){
                    rackViewJPanel.remove( serviceSeparator );
                    rackViewJPanel.repaint();
                }
            }
            else{
                JPanel rackJPanel = policyRackJPanelMap.get(policy);
                // REMOVE FROM RACK MODEL
                policyRackMap.get(policy).remove(buttonKey);
		// SEE IF ALL POLICIES ARE EMPTY
		boolean allEmpty = true;
		for( Policy policy : policyRackMap.keySet() ){
		    if(policyRackMap.get(policy).size()>0){
			allEmpty = false;
			break;
		    }
		}
		// REMOVE SEPARATOR IF ALL EMPTY
		if( allEmpty ){
		    
		}		    
                // REMOVE FROM RACK VIEW
                rackJPanel.remove(mTransformJPanel);
                rackJPanel.revalidate();
            }
        }});
    }
    ///////////////////////////////////////
    // REMOVE API /////////////////////////


    // ADD API ////////////////////////////
    ///////////////////////////////////////
    private void revalidateStore(){
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            storeJPanel.revalidate();
        }});
    }
    private void addToStore(final MackageDesc mackageDesc, final boolean doRevalidate){
        if( !isMackageVisible(mackageDesc) )
            return;
        else if( !isMackageStoreItem(mackageDesc) )
            return;
        final ButtonKey buttonKey = new ButtonKey(mackageDesc);
        final MTransformJButton mTransformJButton = new MTransformJButton(mackageDesc);
        mTransformJButton.setProcurableView(); // xxx i believe this is safe because it ends up on the EDT
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            // UPDATE GUI DATA MODEL
            storeMap.put(buttonKey, mTransformJButton);
            mTransformJButton.addActionListener( new StoreActionListener(mTransformJButton) );
            // UPDATE GUI VIEW MODEL
            int position = ((TreeMap)storeMap).headMap(buttonKey).size();
            storeJPanel.add(mTransformJButton, buttonGridBagConstraints, position);
            if(doRevalidate)
                storeJPanel.revalidate();
        }});
        //System.err.println("Added to store: " + mackageDesc.getDisplayName());
    }
    private void revalidateToolboxes(){
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            serviceToolboxJPanel.revalidate();
            for(JPanel toolboxJPanel : policyToolboxJPanelMap.values())
                toolboxJPanel.revalidate();
        }});
    }
    private void addToToolbox(final Policy policy, final MackageDesc mackageDesc, final boolean isDeployed, final boolean doRevalidate){
        // ONLY UPDATE GUI MODELS IF THIS IS VISIBLE
        if( !isMackageVisible(mackageDesc) )
            return;
        else if( isMackageStoreItem(mackageDesc) )
            return;
        final ButtonKey buttonKey = new ButtonKey(mackageDesc);
        final MTransformJButton mTransformJButton = new MTransformJButton(mackageDesc);
        if( isDeployed )
            mTransformJButton.setDeployedView();
        else
            mTransformJButton.setDeployableView();
        SwingUtilities.invokeLater( new Runnable(){ public void run(){
            if( mackageDesc.isService() ){
                // UPDATE GUI DATA MODEL
                serviceToolboxMap.put(buttonKey,mTransformJButton);
                mTransformJButton.addActionListener( new ToolboxActionListener(null,mTransformJButton) );
                // UPDATE GUI VIEW MODEL
                int position = ((TreeMap)serviceToolboxMap).headMap(buttonKey).size();
                serviceToolboxJPanel.add(mTransformJButton, buttonGridBagConstraints, position);
                if(doRevalidate)
                    serviceToolboxJPanel.revalidate();
            }
            else{
                // UPDATE GUI DATA MODEL
                Map<ButtonKey,MTransformJButton> toolboxMap = policyToolboxMap.get(policy);
                toolboxMap.put(buttonKey,mTransformJButton);
                mTransformJButton.addActionListener( new ToolboxActionListener(policy,mTransformJButton) );
                // UPDATE GUI VIEW MODEL
                JPanel toolboxJPanel = policyToolboxJPanelMap.get(policy);
                int position = ((TreeMap)toolboxMap).headMap(buttonKey).size();
                toolboxJPanel.add(mTransformJButton, buttonGridBagConstraints, position);
                if(doRevalidate)
                    toolboxJPanel.revalidate();
            }
        }});
        System.err.println("Added to toolbox (" + (mackageDesc.isService()?"service":policy.getName()) + "): " + mackageDesc.getDisplayName() + " deployed: " + isDeployed);
    }
    private void revalidateRacks(){
        SwingUtilities.invokeLater( new Runnable() { public void run() {
            serviceRackJPanel.revalidate();
            for( JPanel rackJPanel : policyRackJPanelMap.values() )
                rackJPanel.revalidate();
        }});
    }
    private void addToRack(final Policy policy, final MTransformJPanel mTransformJPanel, final boolean doRevalidate){
        final ButtonKey buttonKey = new ButtonKey(mTransformJPanel);
        SwingUtilities.invokeLater( new Runnable() { public void run() {
            if( mTransformJPanel.getMackageDesc().isService() ){
                // DEAL WITH SPACER
                if( serviceRackMap.isEmpty() ){
                    rackViewJPanel.add( serviceSeparator, serviceSeparatorGridBagConstraints );
                    rackViewJPanel.repaint();
                }
                // ADD TO RACK MODEL
                serviceRackMap.put(buttonKey,mTransformJPanel);
                // UPDATE GUI VIEW MODEL
                int position = ((TreeMap)serviceRackMap).headMap(buttonKey).size();
                serviceRackJPanel.add(mTransformJPanel, applianceGridBagConstraints, position);
                if(doRevalidate)
                    serviceRackJPanel.revalidate();
            }
            else{
                // ADD TO RACK MODEL
                policyRackMap.get(policy).put(buttonKey,mTransformJPanel);
                // UPDATE GUI VIEW MODEL
                final JPanel rackJPanel = policyRackJPanelMap.get(policy);
                int position = ((TreeMap)policyRackMap.get(policy)).headMap(buttonKey).size();
                rackJPanel.add(mTransformJPanel, applianceGridBagConstraints, position);
                if(doRevalidate)
                    rackJPanel.revalidate();
            }
        }});
        System.err.println("Added to rack (" + (mTransformJPanel.getMackageDesc().isService()?"service":policy.getName()) + "): " + mTransformJPanel.getMackageDesc().getDisplayName() );
    }
    ///////////////////////////////////////
    // ADD API ////////////////////////////


    // Private CLASSES & UTILS /////////////////////
    ////////////////////////////////////////////////
    public synchronized MCasingJPanel[] loadAllCasings(boolean generateGuis){
        final String casingNames[] = {"http-casing", "mail-casing", "ftp-casing"};
        Vector<MCasingJPanel> mCasingJPanels = new Vector<MCasingJPanel>();
        List<Tid> casingInstances = null;
        TransformContext transformContext = null;
        TransformDesc transformDesc = null;
        String casingGuiClassName = null;
        Class casingGuiClass = null;
        Constructor casingGuiConstructor = null;
        MCasingJPanel mCasingJPanel = null;
        for(String casingName : casingNames){
            try{
                casingInstances = Util.getTransformManager().transformInstances(casingName);
                if( casingInstances.size() == 0 )
                    continue;
                transformContext = Util.getTransformManager().transformContext(casingInstances.get(0));
                transformDesc = transformContext.getTransformDesc();
                casingGuiClassName = transformDesc.getGuiClassName();
                casingGuiClass = Util.getClassLoader().loadClass( casingGuiClassName, transformDesc );
                if(generateGuis){
                    casingGuiConstructor = casingGuiClass.getConstructor(new Class[]{});
                    mCasingJPanel = (MCasingJPanel) casingGuiConstructor.newInstance(new Object[]{});
                    mCasingJPanels.add(mCasingJPanel);
                }
            }
            catch(Exception e){
                Util.handleExceptionNoRestart("Error loading all casings: " + casingName, e);
            }
        }
        return mCasingJPanels.toArray( new MCasingJPanel[0] );
    }
    private boolean isMackageVisible(MackageDesc mackageDesc){
        if( mackageDesc.getViewPosition() < 0 )
            return false;
        else
            return true;
    }
    private boolean isMackageStoreItem(MackageDesc mackageDesc){
        if( mackageDesc.getName().endsWith("-storeitem") )
            return true;
        else
            return false;
    }
    private class PolicyRenderer implements ListCellRenderer{
        private ListCellRenderer listCellRenderer;
        public PolicyRenderer(ListCellRenderer listCellRenderer){
            this.listCellRenderer = listCellRenderer;
        }
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasFocus){
            return listCellRenderer.getListCellRendererComponent(list, (value instanceof Policy?((Policy)value).getName():value.toString()), index, isSelected, hasFocus);
        }
    }
    private class StoreActionListener implements java.awt.event.ActionListener {
        private MTransformJButton mTransformJButton;
        public StoreActionListener(MTransformJButton mTransformJButton){
            this.mTransformJButton = mTransformJButton;
        }
        public void actionPerformed(java.awt.event.ActionEvent evt){
            if( Util.getIsDemo() )
                return;
            // THE OLD SCHOOL WAY OF PURCHASING
	    /*
            if( (evt.getModifiers() & ActionEvent.SHIFT_MASK) > 0){
                mTransformJButton.setEnabled(false);
                StoreJDialog storeJDialog = new StoreJDialog(mTransformJButton);
                storeJDialog.setVisible(true);
                if( storeJDialog.getPurchasedMTransformJButton() == null ){
                    mTransformJButton.setEnabled(true);
                }
		}*/
            // THE NEW SHITE
	    try{
		String authNonce = Util.getAdminManager().generateAuthNonce();
		URL newURL = new URL( Util.getServerCodeBase(), "../store/storeitem.php?name="
				      + mTransformJButton.getName() + "&" + authNonce);
		((BasicService) ServiceManager.lookup("javax.jnlp.BasicService")).showDocument(newURL);
	    }
	    catch(Exception f){
		Util.handleExceptionNoRestart("error launching browser for Store", f);
		new MOneButtonJDialog("Metavize Store Warning",
				      "A problem occurred while trying to access Store."
				      + "<br>Please contact Metavize support.");
	    }

        }
    }
    private class ToolboxActionListener implements java.awt.event.ActionListener {
        private Policy policy;
        private MTransformJButton mTransformJButton;
        public ToolboxActionListener(Policy policy, MTransformJButton mTransformJButton){
            this.policy = policy;
            this.mTransformJButton = mTransformJButton;
        }
        public void actionPerformed(java.awt.event.ActionEvent evt){
            if( Util.getIsDemo() )
                return;
            if( (evt.getModifiers() & ActionEvent.SHIFT_MASK) > 0){
                new MoveFromToolboxToStoreThread(mTransformJButton);
            }
            else{
                new MoveFromToolboxToRackThread(policy,mTransformJButton);
            }
        }
    }


    //////////////////////////////////////
    // PRIVATE CLASSES AND UTILS /////////
    private void focusInRack(final MTransformJPanel mTransformJPanel){
        SwingUtilities.invokeLater( new Runnable() { public void run() {
            rackJScrollPane.getViewport().validate();
            Rectangle scrollRect = SwingUtilities.convertRectangle(mTransformJPanel.getParent(),
                                                                   mTransformJPanel.getBounds(),
                                                                   rackJScrollPane.getViewport());
            scrollRect.y -= 20;
            scrollRect.height += 40;
            rackJScrollPane.getViewport().scrollRectToVisible(scrollRect);
            mTransformJPanel.highlight();
        }});
    }
    private void focusInToolbox(final MTransformJButton mTransformJButton, final boolean doHighlight){
        SwingUtilities.invokeLater( new Runnable() { public void run() {
            MTransformJButton focusMTransformJButton;
            ButtonKey buttonKey = new ButtonKey(mTransformJButton);
            if( mTransformJButton.getMackageDesc().isService() ){
                focusMTransformJButton = serviceToolboxMap.get(buttonKey);
            }
            else{
                focusMTransformJButton = policyToolboxMap.get(selectedPolicy).get(buttonKey);
            }
            actionJTabbedPane.setSelectedIndex(1);
            toolboxJScrollPane.getViewport().validate();
            Rectangle scrollRect = SwingUtilities.convertRectangle(focusMTransformJButton.getParent(),
                                                                   focusMTransformJButton.getBounds(),
                                                                   toolboxJScrollPane.getViewport());
            toolboxJScrollPane.getViewport().scrollRectToVisible(scrollRect);
            if( doHighlight )
                focusMTransformJButton.highlight();
        } } );
    }



}
