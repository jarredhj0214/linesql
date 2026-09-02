SELECT
  user_id,
  ARRAY_AGG(amount RESPECT NULLS) OVER (PARTITION BY user_id ORDER BY event_time) AS amounts
FROM dwd.orders
