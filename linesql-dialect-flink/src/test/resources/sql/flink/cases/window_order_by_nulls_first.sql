SELECT
  user_id,
  ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY event_time ASC NULLS FIRST) AS rn
FROM dwd.orders
