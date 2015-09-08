{
    "uniqueId": "firewall-cnxNJ0ZXsz",
    "category": "Firewall",
    "description": "The number of flagged session grouped by username.",
    "displayOrder": 602,
    "enabled": true,
    "javaClass": "com.untangle.node.reports.ReportEntry",
    "orderByColumn": "value",
    "orderDesc": true,
    "units": "hits",
    "pieGroupColumn": "username",
    "pieSumColumn": "count(*)",
    "conditions": [
        {
            "javaClass": "com.untangle.node.reports.SqlCondition",
            "column": "firewall_blocked",
            "operator": "=",
            "value": "true"
        }
    ],
    "readOnly": true,
    "table": "sessions",
    "title": "Top Blocked Usernames",
    "type": "PIE_GRAPH"
}
