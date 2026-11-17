EXPLAIN JSON_EXECUTION_PLAN
SELECT e.event_id
FROM ods.events AS e
WHERE e.is_valid;
