SELECT e.id AS user_id, e.event_time AS event_clock
FROM ods.events e
WINDOW grouped AS (PARTITION BY user_id)
QUALIFY ROW_NUMBER() OVER (grouped ORDER BY event_time DESC) <= 2 AND event_clock IS NOT NULL;
