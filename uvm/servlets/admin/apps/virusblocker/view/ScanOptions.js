Ext.define('Ung.apps.virusblocker.view.ScanOptions', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.app-virusblocker-scanoptions',
    itemId: 'scanoptions',
    title: 'Scan Options'.t(),

    bodyPadding: 10,
    layout: {
        type: 'vbox',
    },

    items: [{
        xtype: 'checkbox',
        boxLabel: '<strong>' + 'Scan HTTP'.t() + '</strong> (&copy; BitDefender 1997-2017)',
        bind: '{settings.scanHttp}'
    }, {
        xtype: 'checkbox',
        boxLabel: '<strong>' + 'Scan SMTP'.t() + '</strong>',
        bind: '{settings.scanSmtp}'
    }, {
        xtype: 'combo',
        editable: false,
        fieldLabel: 'Action'.t(),
        labelAlign: 'right',
        queryMode: 'local',
        store: [
            ['pass', 'pass message'.t()],
            ['remove', 'remove infection'.t()],
            ['block', 'block message'.t()]
        ],
        disabled: true,
        bind: {
            value: '{settings.smtpAction}',
            disabled: '{!settings.scanSmtp}'
        }
    }, {
        xtype: 'checkbox',
        boxLabel: '<strong>' + 'Scan FTP'.t() + '</strong>',
        bind: '{settings.scanFtp}'
    }]
});
