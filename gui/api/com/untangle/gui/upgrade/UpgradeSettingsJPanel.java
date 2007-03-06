/*
 * Copyright (c) 2003-2007 Untangle, Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Untangle, Inc. ("Confidential Information"). You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.untangle.gui.upgrade;

import com.untangle.gui.util.Util;
import com.untangle.gui.transform.*;
import com.untangle.mvvm.Period;
import com.untangle.mvvm.toolbox.UpgradeSettings;

import javax.swing.SpinnerDateModel;
import javax.swing.JSpinner;
import javax.swing.JFormattedTextField;
import javax.swing.text.*;
import java.text.*;
import java.util.*;
import java.awt.Color;

public class UpgradeSettingsJPanel extends javax.swing.JPanel
    implements Savable<UpgradeCompoundSettings>, Refreshable<UpgradeCompoundSettings> {
		
    private static final String EXCEPTION_NO_DAY = "You must select at least one day to check for upgrades.";

    public UpgradeSettingsJPanel() {
        initComponents();
        Util.addPanelFocus(this, mondayJCheckBox);
        Util.addFocusHighlight(timeJSpinner);
    }
    
    public void doSave(UpgradeCompoundSettings upgradeCompoundSettings, boolean validateOnly) throws Exception {
        Calendar tempCalendar = new GregorianCalendar();
        tempCalendar.setTime((Date)timeJSpinner.getValue());
        int hour = tempCalendar.get(Calendar.HOUR_OF_DAY);
        int minute = tempCalendar.get(Calendar.MINUTE);

        boolean sunday = sundayJCheckBox.isSelected();
        boolean monday = mondayJCheckBox.isSelected();
        boolean tuesday = tuesdayJCheckBox.isSelected();
        boolean wednesday = wednesdayJCheckBox.isSelected();
        boolean thursday = thursdayJCheckBox.isSelected();
        boolean friday = fridayJCheckBox.isSelected();
        boolean saturday = saturdayJCheckBox.isSelected();

        if( !(sunday || monday || tuesday || wednesday || thursday || friday || saturday) )
            throw new Exception(EXCEPTION_NO_DAY);

        boolean autoUpgrade = yesAutoJRadioButton.isSelected();

        // SAVE SETTINGS //////
        if( !validateOnly ){
            UpgradeSettings upgradeSettings = upgradeCompoundSettings.getUpgradeSettings();
            Period period = upgradeSettings.getPeriod();
            period.setHour( hour );
            period.setMinute( minute );
            period.setSunday( sunday );
            period.setMonday( monday );
            period.setTuesday( tuesday );
            period.setWednesday( wednesday );
            period.setThursday( thursday );
            period.setFriday( friday );
            period.setSaturday( saturday );
            upgradeSettings.setAutoUpgrade( autoUpgrade );
        }
    }

    public void doRefresh(UpgradeCompoundSettings upgradeCompoundSettings) {
        UpgradeSettings upgradeSettings = upgradeCompoundSettings.getUpgradeSettings();

        // BUILD SECOND TAB (SCHEDULED AUTOMATIC UPGRADE)
        int hour, minute;
        if(upgradeSettings.getAutoUpgrade()){
            yesAutoJRadioButton.setSelected(true);
        }
        else{
            noAutoJRadioButton.setSelected(true);
        }
        Period period = upgradeSettings.getPeriod();

        hour = period.getHour();
        minute = period.getMinute();

        // set time
        Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        //set days
        sundayJCheckBox.setSelected( period.getSunday() );
        mondayJCheckBox.setSelected( period.getMonday() );
        tuesdayJCheckBox.setSelected( period.getTuesday() );
        wednesdayJCheckBox.setSelected( period.getWednesday() );
        thursdayJCheckBox.setSelected( period.getThursday() );
        fridayJCheckBox.setSelected( period.getFriday() );
        saturdayJCheckBox.setSelected( period.getSaturday() );
        // set time value
        timeJSpinner.setValue(calendar.getTime());
		((JSpinner.DefaultEditor)timeJSpinner.getEditor()).getTextField().setBackground(Color.WHITE);
    }



        private void initComponents() {//GEN-BEGIN:initComponents
                java.awt.GridBagConstraints gridBagConstraints;

                autoInstallButtonGroup = new javax.swing.ButtonGroup();
                advancedJPanel = new javax.swing.JPanel();
                jPanel3 = new javax.swing.JPanel();
                jLabel1 = new javax.swing.JLabel();
                jPanel5 = new javax.swing.JPanel();
                mondayJCheckBox = new javax.swing.JCheckBox();
                tuesdayJCheckBox = new javax.swing.JCheckBox();
                wednesdayJCheckBox = new javax.swing.JCheckBox();
                thursdayJCheckBox = new javax.swing.JCheckBox();
                fridayJCheckBox = new javax.swing.JCheckBox();
                saturdayJCheckBox = new javax.swing.JCheckBox();
                sundayJCheckBox = new javax.swing.JCheckBox();
                // Create a calendar object and initialize to a particular hour if desired
                Calendar calendar = new GregorianCalendar();
                calendar.set(Calendar.HOUR_OF_DAY, 10);
                calendar.set(Calendar.MINUTE, 0);

                // Create a date spinner that controls the hours
                SpinnerDateModel dateModel = new SpinnerDateModel(calendar.getTime(), null, null, Calendar.MINUTE );
                timeJSpinner = new JSpinner(dateModel);
                // Get the date formatter
                JFormattedTextField tf = ((JSpinner.DefaultEditor)timeJSpinner.getEditor()).getTextField();
                DefaultFormatterFactory factory = (DefaultFormatterFactory)tf.getFormatterFactory();
                DateFormatter formatter = (DateFormatter)factory.getDefaultFormatter();

                // Or use 24 hour mode
                formatter.setFormat(new SimpleDateFormat("HH:mm " + "(" + "a" + ")"));
                timeJSpinner.setValue( calendar.getTime() );
                contentJPanel2 = new javax.swing.JPanel();
                jLabel2 = new javax.swing.JLabel();
                jPanel6 = new javax.swing.JPanel();
                yesAutoJRadioButton = new javax.swing.JRadioButton();
                noAutoJRadioButton = new javax.swing.JRadioButton();

                setLayout(new java.awt.GridBagLayout());

                advancedJPanel.setLayout(new java.awt.GridBagLayout());

                advancedJPanel.setFocusable(false);
                jPanel3.setLayout(new java.awt.GridBagLayout());

                jPanel3.setOpaque(false);
                jLabel1.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel1.setText("Upgrade Check Days");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                jPanel3.add(jLabel1, gridBagConstraints);

                jPanel5.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

                jPanel5.setBackground(new java.awt.Color(213, 213, 226));
                jPanel5.setBorder(new javax.swing.border.EtchedBorder());
                jPanel5.setMinimumSize(new java.awt.Dimension(21, 185));
                jPanel5.setOpaque(false);
                jPanel5.setPreferredSize(new java.awt.Dimension(110, 250));
                mondayJCheckBox.setFont(new java.awt.Font("Default", 0, 12));
                mondayJCheckBox.setText("Monday");
                mondayJCheckBox.setDoubleBuffered(true);
                mondayJCheckBox.setOpaque(false);
                jPanel5.add(mondayJCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 40, 91, -1));

                tuesdayJCheckBox.setFont(new java.awt.Font("Default", 0, 12));
                tuesdayJCheckBox.setText("Tuesday");
                tuesdayJCheckBox.setDoubleBuffered(true);
                tuesdayJCheckBox.setOpaque(false);
                jPanel5.add(tuesdayJCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 60, 91, -1));

                wednesdayJCheckBox.setFont(new java.awt.Font("Default", 0, 12));
                wednesdayJCheckBox.setText("Wednesday");
                wednesdayJCheckBox.setDoubleBuffered(true);
                wednesdayJCheckBox.setOpaque(false);
                jPanel5.add(wednesdayJCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 80, -1, -1));

                thursdayJCheckBox.setFont(new java.awt.Font("Default", 0, 12));
                thursdayJCheckBox.setText("Thursday");
                thursdayJCheckBox.setDoubleBuffered(true);
                thursdayJCheckBox.setOpaque(false);
                jPanel5.add(thursdayJCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 100, 91, -1));

                fridayJCheckBox.setFont(new java.awt.Font("Default", 0, 12));
                fridayJCheckBox.setText("Friday");
                fridayJCheckBox.setDoubleBuffered(true);
                fridayJCheckBox.setOpaque(false);
                jPanel5.add(fridayJCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 120, 91, -1));

                saturdayJCheckBox.setFont(new java.awt.Font("Default", 0, 12));
                saturdayJCheckBox.setText("Saturday");
                saturdayJCheckBox.setDoubleBuffered(true);
                saturdayJCheckBox.setOpaque(false);
                jPanel5.add(saturdayJCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 140, 91, -1));

                sundayJCheckBox.setFont(new java.awt.Font("Default", 0, 12));
                sundayJCheckBox.setText("Sunday");
                sundayJCheckBox.setDoubleBuffered(true);
                sundayJCheckBox.setOpaque(false);
                jPanel5.add(sundayJCheckBox, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 160, 91, -1));

                timeJSpinner.setFont(new java.awt.Font("Default", 0, 12));
                timeJSpinner.setDoubleBuffered(true);
                timeJSpinner.setMaximumSize(new java.awt.Dimension(100, 19));
                timeJSpinner.setMinimumSize(new java.awt.Dimension(100, 19));
                timeJSpinner.setPreferredSize(new java.awt.Dimension(100, 19));
                jPanel5.add(timeJSpinner, new org.netbeans.lib.awtextra.AbsoluteConstraints(45, 200, 100, 19));

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.ipadx = 70;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                jPanel3.add(jPanel5, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.ipadx = 40;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(15, 15, 15, 15);
                advancedJPanel.add(jPanel3, gridBagConstraints);

                contentJPanel2.setLayout(new java.awt.GridBagLayout());

                jLabel2.setFont(new java.awt.Font("Dialog", 0, 12));
                jLabel2.setText("Automatic Installation Of Upgrades");
                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 0;
                contentJPanel2.add(jLabel2, gridBagConstraints);

                jPanel6.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

                jPanel6.setBackground(new java.awt.Color(213, 213, 226));
                jPanel6.setBorder(new javax.swing.border.EtchedBorder());
                jPanel6.setMinimumSize(new java.awt.Dimension(21, 185));
                jPanel6.setOpaque(false);
                jPanel6.setPreferredSize(new java.awt.Dimension(110, 250));
                autoInstallButtonGroup.add(yesAutoJRadioButton);
                yesAutoJRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                yesAutoJRadioButton.setText("<html> <b>Automatically install new upgrades</b><br>\nIf new upgrades are found after a \"Scheduled<br>\nAutomatic Upgrade\", those upgrades will be<br>\nautomatically downloaded and installed.<br>\nIn the case of certain critical system upgrades,<br>\nthe system may be automatically restarted, and<br>\nthe user interface may not connect for a short<br>\nperiod of time. </html>");
                yesAutoJRadioButton.setDoubleBuffered(true);
                yesAutoJRadioButton.setOpaque(false);
                yesAutoJRadioButton.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
                jPanel6.add(yesAutoJRadioButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 10, -1, -1));

                autoInstallButtonGroup.add(noAutoJRadioButton);
                noAutoJRadioButton.setFont(new java.awt.Font("Dialog", 0, 12));
                noAutoJRadioButton.setSelected(true);
                noAutoJRadioButton.setText("<html>\n<b>Do not automatically install new upgrades</b><br>\nIf new upgrades are found after a \"Scheduled<br>\nAutomatic Upgrade\",  those upgrades will NOT<br>\nbe automatically installed.  The system<br>\nadministrator must manually upgrade the system<br>\nthrough the \"Manual Upgrade\" tab of this window.\n</html>");
                noAutoJRadioButton.setDoubleBuffered(true);
                noAutoJRadioButton.setOpaque(false);
                noAutoJRadioButton.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
                jPanel6.add(noAutoJRadioButton, new org.netbeans.lib.awtextra.AbsoluteConstraints(30, 140, -1, -1));

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 0;
                gridBagConstraints.gridy = 1;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                contentJPanel2.add(jPanel6, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.gridx = 1;
                gridBagConstraints.gridy = 0;
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                gridBagConstraints.insets = new java.awt.Insets(15, 5, 15, 15);
                advancedJPanel.add(contentJPanel2, gridBagConstraints);

                gridBagConstraints = new java.awt.GridBagConstraints();
                gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
                gridBagConstraints.weightx = 1.0;
                gridBagConstraints.weighty = 1.0;
                add(advancedJPanel, gridBagConstraints);

        }//GEN-END:initComponents
		
		
        // Variables declaration - do not modify//GEN-BEGIN:variables
        private javax.swing.JPanel advancedJPanel;
        private javax.swing.ButtonGroup autoInstallButtonGroup;
        private javax.swing.JPanel contentJPanel2;
        private javax.swing.JCheckBox fridayJCheckBox;
        private javax.swing.JLabel jLabel1;
        private javax.swing.JLabel jLabel2;
        private javax.swing.JPanel jPanel3;
        private javax.swing.JPanel jPanel5;
        private javax.swing.JPanel jPanel6;
        private javax.swing.JCheckBox mondayJCheckBox;
        private javax.swing.JRadioButton noAutoJRadioButton;
        private javax.swing.JCheckBox saturdayJCheckBox;
        private javax.swing.JCheckBox sundayJCheckBox;
        private javax.swing.JCheckBox thursdayJCheckBox;
        private javax.swing.JSpinner timeJSpinner;
        private javax.swing.JCheckBox tuesdayJCheckBox;
        private javax.swing.JCheckBox wednesdayJCheckBox;
        private javax.swing.JRadioButton yesAutoJRadioButton;
        // End of variables declaration//GEN-END:variables
		
}
