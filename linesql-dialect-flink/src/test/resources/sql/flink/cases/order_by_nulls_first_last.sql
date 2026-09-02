SELECT user_id, event_time
FROM dwd.orders
ORDER BY event_time DESC NULLS LAST, user_id ASC NULLS FIRST
