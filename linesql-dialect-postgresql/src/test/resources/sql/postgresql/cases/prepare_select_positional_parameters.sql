PREPARE recent_orders (bigint, integer) AS
SELECT o.id, o.amount * $2 AS adjusted_amount
FROM sales.orders AS o
WHERE o.customer_id = $1
ORDER BY o.created_at DESC LIMIT $2;
