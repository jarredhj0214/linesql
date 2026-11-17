EXECUTE WITH current_events (event_id) AS (
    SELECT id FROM ods.events WHERE active
)
SELECT event_id FROM current_events
WHERE event_id > 0
ORDER BY event_id;
