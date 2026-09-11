SELECT user_id,
       sum(amount) OVER w AS total_amount
FROM sales.orders
WINDOW w AS (PARTITION BY user_id ORDER BY created_at ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
