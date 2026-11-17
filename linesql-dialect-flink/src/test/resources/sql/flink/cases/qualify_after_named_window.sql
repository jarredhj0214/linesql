SELECT s.id AS user_id, ROW_NUMBER() OVER w AS rn
FROM ods.events AS s
WINDOW w AS (PARTITION BY user_id ORDER BY s.event_time DESC)
QUALIFY rn <= 2;
