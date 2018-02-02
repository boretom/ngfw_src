Ext.define('Ung.config.network.view.NatRules', {
    extend: 'Ext.Panel',
    alias: 'widget.config-network-nat-rules',
    itemId: 'nat-rules',
    scrollable: true,

    viewModel: true,

    title: 'NAT Rules'.t(),

    layout: 'fit',

    items: [{
        xtype: 'mastergrid',
        flex: 3,

        enableMove: true,
        enableDelete: true,

        settingsProperty: 'natRules',
        conditions: [
            Condition.HOST_IN_PENALTY_BOX,
            Condition.DST_ADDR,
            Condition.DST_PORT,
            Condition.DST_INTF,
            Condition.SRC_ADDR,
            Condition.SRC_PORT,
            Condition.SRC_INTF,
            Condition.PROTOCOL,
            Condition.CLIENT_TAGGED,
            Condition.SERVER_TAGGED
        ],
        conditionClass: 'com.untangle.uvm.network.NatRuleCondition',
        newRecord: {
            ruleId: -1,
            enabled: true,
            auto: true,
            javaClass: 'com.untangle.uvm.network.NatRule',
            conditions: {
                javaClass: 'java.util.LinkedList',
                list: []
            },
            description: ''
        },

        plugins: {
            gridcellediting: true,
            gridviewoptions: false
        },

        // defaults: {
        //     menuDisabled: true
        // },

        sortable: false,

        bind: '{natRules}',

        columnsDef: [
            Column.RULEID,
            Column.ENABLED,
            Column.DESCRIPTION,
            Column.CONDITIONS,
            Column.NAT_TYPE, {
                xtype: 'gridcolumn',
                text: 'New Source'.t(),
                dataIndex: 'newSource',
                width: 150,
                editable: true,
                editor: {
                    xtype: 'textfield'
                }
                // renderer: Ung.config.network.MainController.natNewSourceRenderer
            }]
    }, {
        xtype: 'paneldescription',
        html: 'NAT Rules control the rewriting of the IP source address of traffic (Network Address Translation). The rules are evaluated in order.'.t()
    }]
});
