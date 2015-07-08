{
    "category": "Spam Blocker Lite",
    "conditions": [
        {
            "column": "vendor_name",
            "javaClass": "com.untangle.node.reporting.SqlCondition",
            "operator": "=",
            "value": "'spam_blocker_lite'"
        }
    ],
    "defaultColumns": ["time_stamp","hostname","ipaddr"],
    "displayOrder": 40,
    "javaClass": "com.untangle.node.reporting.EventEntry",
    "table": "smtp_tarpit_events",
    "title": "Tarpit Events",
    "uniqueId": "spam-blocker-lite-2FGBJUJE9W"
}
