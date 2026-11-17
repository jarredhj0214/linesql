SELECT 0, s.amount + s.tax, s.customer_id AS id
FROM sales.orders AS s
ORDER BY 2 DESC, 3;
