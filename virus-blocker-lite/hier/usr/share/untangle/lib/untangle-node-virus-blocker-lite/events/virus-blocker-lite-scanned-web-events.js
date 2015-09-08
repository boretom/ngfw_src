{
    "category": "Virus Blocker Lite",
    "conditions": [
        {
            "column": "virus_blocker_lite_clean",
            "javaClass": "com.untangle.node.reports.SqlCondition",
            "operator": "is",
            "value": "NOT NULL"
        }
    ],
    "defaultColumns": ["time_stamp","hostname","username","host","uri","virus_blocker_lite_clean","virus_blocker_lite_name","s_server_addr","s_server_port"],
    "description": "All HTTP sessions scanned by Virus Blocker Lite.",
    "displayOrder": 10,
    "javaClass": "com.untangle.node.reports.EventEntry",
    "table": "http_events",
    "title": "Scanned Web Events",
    "uniqueId": "virus-blocker-lite-XZ3QSNQ2NP"
}
