SELECT user_id,
       avg(amount) OVER w2 AS avg_amount
FROM sales.orders
WINDOW w1 AS (PARTITION BY user_id),
       w2 AS (w1 ORDER BY created_at)
