{
    "category": "Phish Blocker",
    "conditions": [
        {
            "column": "addr_kind",
            "javaClass": "com.untangle.node.reports.SqlCondition",
            "operator": "=",
            "value": "B"
        },
        {
            "column": "phish_blocker_is_spam",
            "javaClass": "com.untangle.node.reports.SqlCondition",
            "operator": "is",
            "value": "TRUE"
        }
    ],
    "defaultColumns": ["time_stamp","hostname","s_server_addr","addr","sender","subject","phish_blocker_is_spam","phish_blocker_action","phish_blocker_score"],
    "description": "All email sessions detected as phishing attempts.",
    "displayOrder": 20,
    "javaClass": "com.untangle.node.reports.EventEntry",
    "table": "mail_addrs",
    "title": "All Phish Events",
    "uniqueId": "phish-blocker-M5PBL5CN1B"
}
