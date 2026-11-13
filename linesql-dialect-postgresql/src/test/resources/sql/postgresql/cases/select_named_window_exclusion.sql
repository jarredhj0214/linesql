SELECT SUM(o.amount) OVER running AS running_total
FROM sales.orders AS o
WINDOW base AS (PARTITION BY o.customer_id),
       running AS (base ORDER BY o.created_at ROWS UNBOUNDED PRECEDING EXCLUDE CURRENT ROW);
