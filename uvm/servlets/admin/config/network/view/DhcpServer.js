Ext.define('Ung.config.network.view.DhcpServer', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.config-network-dhcp-server',
    itemId: 'dhcp-server',
    viewModel: true,
    scrollable: true,
    layout: 'fit',
    title: 'DHCP Server'.t(),

    items: [{
        xtype: 'tabpanel',
        itemId: 'dhcpserver',
        layout: 'fit',

        items: [{
            title: 'Server'.t(),
            itemId: 'x-dhcp-server',
            layout: 'border',
            scrollable: true,
            items: [{
                region: "west",
                xtype: 'ungrid',
                width: '50%',
                itemId: 'dhcpEntries',
                title: 'Static Entries'.t(),

                tbar: ['@add', '->', '@import', '@export'],
                recordActions: ['delete'],

                emptyText: 'No Static Entries defined'.t(),

                listProperty: 'settings.staticDhcpEntries.list',

                emptyRow: {
                    macAddress: '11:22:33:44:55:66',
                    address: '1.2.3.4',
                    javaClass: 'com.untangle.uvm.network.DhcpStaticEntry',
                    description: '[no description]'.t()
                },

                bind: '{staticDhcpEntries}',

                columns: [{
                    header: 'MAC Address'.t(),
                    dataIndex: 'macAddress',
                    width: 200,
                    editor: {
                        xtype:'textfield',
                        emptyText: '[enter MAC address]'.t(),
                        allowBlank: false,
                        vtype: 'macAddress',
                        maskRe: /[a-fA-F0-9:]/
                    }
                }, {
                    header: 'Address'.t(),
                    width: 200,
                    dataIndex: 'address',
                    editor: {
                        xtype: 'textfield',
                        emptyText: '[enter address]'.t(),
                        allowBlank: false,
                        vtype: 'ipAddress'
                    }
                }, {
                    header: 'Description'.t(),
                    flex: 1,
                    dataIndex: 'description',
                    editor: {
                        xtype: 'textfield',
                        emptyText: '[enter description]'.t(),
                        allowBlank: false
                    }
                }],
                editorFields: [
                    Field.macAddress,
                    Field.ipAddress,
                    Field.description
                ]
            }, {
                xtype: 'ungrid',
                width: '50%',
                region: "east",
                itemId: 'dhcpLeases',
                title: 'Current Leases'.t(),
                enableColumnHide: false,
                enableColumnMove: false,

                emptyText: 'No Current Leases defined'.t(),

                tbar: [{
                    text: 'Refresh'.t(),
                    iconCls: 'fa fa-refresh',
                    handler: 'externalAction',
                    action: 'refreshDhcpLeases'
                }],

                bind: '{dynamicDhcpEntries}',

                columns: [{
                    header: 'MAC Address'.t(),
                    dataIndex:'macAddress',
                    width: Renderer.macWidth
                },{
                    header: 'Address'.t(),
                    dataIndex: 'address',
                    width: Renderer.ipWidth
                },{
                    header: 'Hostname'.t(),
                    dataIndex: 'hostname',
                    width: Renderer.hostnameWidth,
                    flex: 1
                },{
                    header: 'Expiration Time'.t(),
                    dataIndex: 'date',
                    width: Renderer.timestampWidth,
                    renderer: Renderer.timestamp
                }, {
                    xtype: 'actioncolumn',
                    width: Renderer.actionColumn,
                    header: 'Add Static'.t(),
                    align: 'center',
                    iconCls: 'fa fa-plus',
                    sortable: false,
                    resizable: false,
                    handler: 'externalAction',
                    action: 'addStaticDhcpLease'
                }],
            }]
        },{
            title: 'Relays'.t(),
            layout: 'fit',
            scrollable: true,
            items: [{
                xtype: 'ungrid',
                width: '100%',
                itemId: 'XXdhcpRelays',
                title: 'Relays'.t(),

                tbar: ['@add', '->', '@import', '@export'],
                recordActions: ['edit','delete'],

                emptyText: 'No Relays defined'.t(),

                listProperty: 'settings.dhcpRelays.list',
                bind: '{dhcpRelays}',

                emptyRow: {
                    enabled: true,
                    description: '',
                    rangeStart: '',
                    rangeEnd: '',
                    leaseDuration: 3600,
                    gateway: '',
                    prefix: 24,
                    dns: '',
                    javaClass: 'com.untangle.uvm.network.DhcpRelay',
                    options: {
                        javaClass: 'java.util.LinkedList',
                        list: []
                    },
                },

                columns: [
                Column.enabled,
                Column.description,
                {
                    header: 'Range Start'.t(),
                    dataIndex: 'rangeStart',
                    editor: {
                        xtype: 'textfield',
                        emptyText: '[start of range]'.t(),
                        allowBlank: false,
                        vtype: 'ipAddress'
                    }
                }, {
                    header: 'Range End'.t(),
                    dataIndex: 'rangeEnd',
                    editor: {
                        xtype: 'textfield',
                        emptyText: '[end of range]'.t(),
                        allowBlank: false,
                        vtype: 'ipAddress'
                    }
                }, {
                    header: 'Lease Duration'.t(),
                    dataIndex: 'leaseDuration',
                    width: 60,
                    editor: {
                        xtype: 'numberfield',
                        allowBlank : true,
                        allowDecimals: false,
                        minValue: 1
                    }
                }, {
                    header: 'Gateway Address'.t(),
                    dataIndex: 'gateway',
                    editor: {
                        xtype: 'textfield',
                        emptyText: '[dhcp gateway]'.t(),
                        allowBlank: false,
                        vtype: 'ipAddress'
                    }
                }, {
                    header: 'Prefix'.t(),
                    dataIndex: 'prefix',
                    width: 50
                }, {
                    header: 'DNS Address'.t(),
                    dataIndex: 'dns',
                    editor: {
                        xtype: 'textfield',
                        emptyText: '[dns resolver]'.t(),
                        allowBlank: false,
                        vtype: 'ipAddress'
                    }
                }, {
                    header: 'Options'.t(),
                    dataIndex: 'options',
                    flex: 1,
                    renderer: function(value, meta) {
                        if (! ("list" in value) ){
                            return "";
                        }
                        var summary = [];
                        for (var i = 0; i < value.list.length; i++){
                            var option = value.list[i]; 
                            summary.push(option.description + " - " + option.value);
                        }
                        meta.tdAttr = 'data-qtip="' + summary.join(', ') + '"';
                        return summary.join(', ');
                    }
                }],

                editorFields: [
                    Field.enableRule(),
                    Field.description,
                {
                    xtype: 'textfield',
                    fieldLabel: 'Range Start'.t(),
                    emptyText: '[enter address]'.t(),
                    bind: '{record.rangeStart}',
                    allowBlank: false,
                    vtype: 'ipAddress',
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Range End'.t(),
                    emptyText: '[enter address]'.t(),
                    bind: '{record.rangeEnd}',
                    allowBlank: false,
                    vtype: 'ipAddress',
                },{
                    xtype: 'fieldcontainer',
                    layout: 'column',
                    width: '100%',
                    items: [{
                        xtype: 'numberfield',
                        fieldLabel: 'Lease Duration'.t(),
                        labelWidth: 180,
                        width: 350,
                        labelAlign: 'right',
                        minValue: 1,
                        bind: {
                            value: '{record.leaseDuration}'
                        },
                        allowBlank: false
                    }, {
                        xtype: 'displayfield',
                        margin: '0 5',
                        fieldStyle: {
                            color: '#777',
                            fontSize: 'smaller',
                            minHeight: 'auto'
                        },
                        value: '(seconds)'.t()
                    }]
                },{
                    xtype: 'textfield',
                    fieldLabel: 'Gateway'.t(),
                    emptyText: '[enter address]'.t(),
                    bind: '{record.gateway}',
                    allowBlank: false,
                    vtype: 'ipAddress',
                }, {
                    xtype: 'combo',
                    bind: '{record.prefix}',
                    fieldLabel: 'Netmask'.t(),
                    editable: false,
                    store: Util.getV4NetmaskList(false),
                    queryMode: 'local'
                },{
                    xtype: 'textfield',
                    fieldLabel: 'DNS'.t(),
                    emptyText: '[enter address]'.t(),
                    bind: '{record.dns}',
                    allowBlank: false,
                    vtype: 'ipAddress',
                }, {
                    // DHCP options
                    xtype: 'ungrid',
                    title: 'DHCP Options'.t(),
                    emptyText: 'No DHCP options defined'.t(),
                    border: true,
                    collapsible: false,
                    titleCollapse: true,
                    animCollapse: false,
                    disabled: false,
                    bind: {
                        store: '{options}'
                    },
                    listProperty: 'options',
                    tbar: ['@addInline'],
                    recordActions: ['delete'],
                    emptyRow: {
                        enabled: true,
                        value: '66,1.2.3.4',
                        description: ''.t(),
                        javaClass: 'com.untangle.uvm.network.DhcpOption'
                    },
                    columns: [{
                        header: 'Enable'.t(),
                        xtype: 'checkcolumn',
                        dataIndex: 'enabled',
                        align: 'center',
                        width: Renderer.booleanWidth,
                        resizable: false
                    }, {
                        header: 'Description'.t(),
                        dataIndex: 'description',
                        flex: 1,
                        editor : {
                            xtype: 'textfield',
                            emptyText: '[enter description]'.t(),
                            allowBlank:false
                        }
                    }, {
                        header: 'Value'.t(),
                        dataIndex: 'value',
                        width: 150,
                        editor : {
                            xtype: 'textfield',
                            emptyText: '[enter value]'.t(),
                            allowBlank: false
                        }
                    }],
                }]
            }]
        }]
    }]
});
