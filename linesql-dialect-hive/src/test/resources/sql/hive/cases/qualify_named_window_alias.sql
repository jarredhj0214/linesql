SELECT e.id, ROW_NUMBER() OVER recent AS seq
FROM ods.events AS e
WINDOW recent AS (PARTITION BY e.user_id ORDER BY e.event_time DESC)
QUALIFY seq = 1;
