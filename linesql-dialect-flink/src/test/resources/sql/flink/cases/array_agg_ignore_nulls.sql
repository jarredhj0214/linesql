SELECT
  user_id,
  ARRAY_AGG(amount IGNORE NULLS) AS amounts
FROM dwd.orders
GROUP BY user_id
