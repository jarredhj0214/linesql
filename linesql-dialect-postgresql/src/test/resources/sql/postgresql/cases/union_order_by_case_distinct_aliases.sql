SELECT live_value AS "Metric", archived_value AS METRIC FROM ods.live_snapshots
UNION ALL
SELECT current_value, previous_value FROM ods.archived_snapshots
ORDER BY metric;
