Ext.define('Ung.apps.bandwidthcontrol.ConfWizard', {
    extend: 'Ext.window.Window',
    alias: 'widget.app-bandwidthcontrol-wizard',
    title: '<i class="fa fa-magic"></i> ' + 'Bandwidth Control Setup Wizard'.t(),
    modal: true,

    controller: 'app-bandwidthcontrol-wizard',
    viewModel: {
        type: 'app-bandwidthcontrol-wizard'
    },

    width: 800,
    height: 600,

    layout: 'card',

    defaults: {
        border: false,
        scrollable: 'y'
    },

    items: [{
        title: 'Welcome'.t(),
        header: false,
        itemId: 'welcome',
        items: [{
            xtype: 'component',
            html: '<h2>' + 'Welcome to the Bandwidth Control Setup Wizard!'.t() + '</h2>',
            margin: '10'
        }, {
            xtype: 'component',
            html: 'This wizard will help guide you through your initial setup and configuration of Bandwidth Control.'.t(),
            margin: '0 0 10 10'
        }, {
            xtype: 'component',
            html: 'Bandwidth Control leverages information provided by other applications in the rack.'.t() + '<br/><br/>' +
                'Web Filter (non-Lite) provides web site categorization.'.t() + '<br/>' +
                'Application Control provides protocol profiling categorization.'.t() + '<br/>' +
                'Application Control Lite provides protocol profiling categorization.'.t() + '<br/>' +
                'Directory Connector provides username/group information.'.t() + '<br/><br/>' +
                '<strong>' + 'For optimal Bandwidth Control performance install these applications.'.t() + '</strong>',
            margin: '0 0 10 10'
        }, {
            xtype: 'component',
            html: 'WARNING: Completing this setup wizard will overwrite the previous settings with new settings. All previous settings will be lost!'.t(),
            margin: '0 0 10 10'
        }]
    }, {
        title: 'WAN Bandwidth'.t(),
        itemId: 'wan',
        layout: {
            type: 'vbox',
            align: 'stretch'
        },
        items: [{
            xtype: 'component',
            html: '<h2>' + 'Configure WANs download and upload bandwidths'.t() + '</h2>',
            margin: '10 10 0 10'
        }, {
            xtype: 'component',
            html: Ext.String.format('{0}Note:{1} When enabling QoS valid Download Bandwidth and Upload Bandwidth limits must be set for all WAN interfaces.'.t(), '<font color="red">','</font>') + '<br/>' +
                'It is suggested to set these around 95% to 100% of the actual measured bandwidth available for each WAN.'.t(),
            margin: 10
        }, {
            xtype: 'component',
            html: '<i class="fa fa-exclamation-triangle fa-red"></i> <span style="color: red;">' + 'WARNING: These settings must be reasonably accurate for Bandwidth Control to operate properly!'.t() + '</span> <br/><br/>' +
                Ext.String.format('Total: {0} kbps ({1} Mbit) download, {2} kbps ({3} Mbit) upload'.t(), 'd', 'd_Mbit', 'u', 'u_Mbit'),
            margin: 10
        }, {
            xtype: 'grid',
            title: 'WANs'.t(),
            flex: 1,
            trackMouseOver: false,
            sortableColumns: false,
            enableColumnHide: false,
            forceFit: true,

            viewConfig: {
                emptyText: '<p style="text-align: center; margin: 0; line-height: 2;"><i class="fa fa-exclamation-triangle fa-2x"></i> <br/>No WANs..</p>',
                stripeRows: false
            },
            plugins: {
                ptype: 'cellediting',
                clicksToEdit: 1
            },

            bind: {
                store: {
                    data: '{interfaces}',
                    filters: [
                        { property: 'configType', value: 'ADDRESSED' },
                        { property: 'isWan', value: true }
                    ]
                }
            },

            columns: [{
                header: 'Interface Id'.t(),
                align: 'right',
                width: 100,
                resizable: false,
                dataIndex: 'interfaceId'
            }, {
                header: 'WAN'.t(),
                flex: 1,
                dataIndex: 'name'
            }, {
                header: 'Download Bandwidth'.t(),
                width: 150,
                resizable: false,
                dataIndex: 'downloadBandwidthKbps',
                editor: {
                    xtype: 'numberfield',
                    allowBlank : false,
                    allowDecimals: false,
                    minValue: 0
                },
                renderer: function (value) {
                    return Ext.isEmpty(value) ? 'Not set'.t() : (value + ' ' + 'kbps'.t());
                }
            }, {
                header: 'Upload Bandwidth'.t(),
                width: 150,
                resizable: false,
                dataIndex: 'uploadBandwidthKbps',
                editor: {
                    xtype: 'numberfield',
                    allowBlank : false,
                    allowDecimals: false,
                    minValue: 0
                },
                renderer: function (value) {
                    return Ext.isEmpty(value) ? 'Not set'.t() : (value + ' ' + 'kbps'.t());
                }
            }]
        }]
    }, {
        title: 'Configuration'.t(),
        itemId: 'configuration',
        items: [{
            xtype: 'component',
            html: '<h2>' + 'Choose a starting configuration'.t() + '</h2>',
            margin: '10 10 0 10'
        }, {
            xtype: 'component',
            html: 'Several initial default configurations are available for Bandwidth Control. Please select the environment most like yours below.'.t(),
            margin: 10
        }, {
            xtype: 'combo',
            fieldLabel: 'Configuration'.t(),
            margin: 10,
            editable: false,
            bind: '{selectedConf}',
            store: [
                ['business_business', 'Business'.t()],
                ['school_school', 'School'.t()],
                ['school_college', 'College/University'.t()],
                ['business_government', 'Government'.t()],
                ['business_nonprofit', 'Non-Profit'.t()],
                ['school_hotel', 'Hotel'.t()],
                ['home', 'Home'.t()],
                ['metered', 'Metered Internet'.t()],
                ['custom', 'Custom'.t()]
            ]
        }, {
            xtype: 'component',
            margin: 10,
            bind: {
                html: '{confDetails}'
            }
        }]
    }, {
        title: 'Quotas'.t(),
        itemId: 'quotas',
        items: [{
            xtype: 'component',
            html: '<h2>' + 'Configure Quotas'.t() + '</h2>',
            margin: '10 10 0 10'
        }, {
            xtype: 'component',
            html: 'Quotas for bandwidth can be set for certain hosts. This allows some hosts to be allocated high bandwidth, as long as it is remains within a certain usage quota; however, their bandwidth will be slowed if their usage is excessive.'.t(),
            margin: 10
        }, {
            xtype: 'fieldset',
            checkboxToggle: true,
            checkbox: {
                bind: {
                    value: '{quota.enabled}'
                }
            },
            collapsible: true,
            title: 'Enable Quotas'.t(),
            margin: 10,
            padding: 10,
            items: [{
                xtype: 'component',
                margin: '0 0 5 0',
                html: '<strong>' + 'Quota Clients'.t() + '</strong><br/>' +
                    '<span style="font-size: 11px; color: #555;">' + 'controls which hosts will be given quotas.'.t() + '<br/>' + '(Example: 192.168.1.1/24 or 192.168.1.100-192.168.1.200)'.t() + '</span>'
            }, {
                xtype: 'textfield',
                width: 300,
                bind: '{quota.clients}',
                allowBlank: false,
                vtype: 'ip4Address'
            }, {
                xtype: 'component',
                margin: '10 0 5 0',
                html: '<strong>' + 'Quota Expiration'.t() + '</strong><br/>' +
                    '<span style="font-size: 11px; color: #555;">' + 'controls how long a quota lasts (hourly, daily, weekly). The default is Daily.'.t() + '</span>'
            }, {
                xtype: 'combo',
                allowBlank: false,
                editable: false,
                forceSelection: false,
                store: [
                    [-3, 'End of Week'.t()], //END_OF_WEEK from QuotaBoxEntry
                    [-2, 'End of Day'.t()], //END_OF_DAY from QuotaBoxEntry
                    [-1, 'End of Hour'.t()] //END_OF_HOUR from QuotaBoxEntry
                ],
                queryMode: 'local',
                bind: '{quota.expiration}'
            }, {
                xtype: 'component',
                margin: '10 0 5 0',
                html: '<strong>' + 'Quota Size'.t() + '</strong><br/>' +
                    '<span style="font-size: 11px; color: #555;">' + 'configures the size of the quota given to each host. The default is 1 Gb.'.t() + '</span>'
            }, {
                xtype: 'container',
                // width: 200,
                layout: {
                    type: 'hbox'
                },
                items: [{
                    xtype: 'numberfield',
                    allowBlank: false,
                    width: 80,
                    bind: '{quota.size}'
                }, {
                    xtype: 'combo',
                    margin: '0 0 0 5',
                    width: 100,
                    editable: false,
                    store: [
                        [1, 'bytes'.t()],
                        [1000, 'Kilobytes'.t()],
                        [1000000, 'Megabytes'.t()],
                        [1000000000, 'Gigabytes'.t()],
                        [1000000000000, 'Terrabytes'.t()]
                    ],
                    queryMode: 'local',
                    bind: '{quota.unit}'
                }]
            }, {
                xtype: 'component',
                margin: '10 0 5 0',
                html: '<strong>' + 'Quota Exceeded Priority'.t() + '</strong><br/>' +
                    '<span style="font-size: 11px; color: #555;">' + 'configures the priority given to hosts that have exceeded their quota.'.t() + '</span>'
            }, {
                xtype: 'combo',
                allowBlank: false,
                width: 200,
                editable: false,
                store: [
                    [1, 'Very High'.t()],
                    [2, 'High'.t()],
                    [3, 'Medium'.t()],
                    [4, 'Low'.t()],
                    [5, 'Limited'.t()],
                    [6, 'Limited More'.t()],
                    [7, 'Limited Severely'.t()]
                ],
                queryMode: 'local',
                bind: '{quota.priority}'
            }]
        }]
    }, {
        title: 'Finish'.t(),
        itemId: 'finish',
        items: [{
            xtype: 'component',
            html: '<h2>' + 'Congratulations'.t() + '</h2>',
            margin: '10 10 0 10'
        }, {
            xtype: 'component',
            html: '<strong>' + 'Bandwidth Control is now configured and enabled.'.t() + '</strong>',
            margin: 10
        }]
    }],

    dockedItems: [{
        xtype: 'toolbar',
        dock: 'bottom',
        ui: 'footer',
        defaults: {
            minWidth: 200
        },
        items: [{
            hidden: true,
            bind: {
                text: 'Previous'.t() + ' - <strong>' + '{prevBtnText}' + '</strong>',
                hidden: '{!prevBtn}'
            },
            iconCls: 'fa fa-chevron-circle-left',
            handler: 'onPrev'
        }, '->',  {
            hidden: true,
            bind: {
                text: 'Next'.t() + ' - <strong>' + '{nextBtnText}' + '</strong>',
                hidden: '{!nextBtn}'
            },
            iconCls: 'fa fa-chevron-circle-right',
            iconAlign: 'right',
            handler: 'onNext'
        }, {
            text: 'Close'.t(),
            hidden: true,
            bind: {
                hidden: '{nextBtn}'
            },
            iconCls: 'fa fa-check',
            // handler: 'onNext'
        }]
    }]


});
