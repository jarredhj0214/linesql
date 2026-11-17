SELECT s.id, ROW_NUMBER() OVER w AS rn
FROM ods.events AS s
QUALIFY rn <= 2
WINDOW w AS (PARTITION BY s.user_id ORDER BY s.event_time DESC);
