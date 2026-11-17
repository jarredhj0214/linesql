SELECT o.id AS first_id, o.*, o.status AS trailing_status
FROM sales.orders AS o
ORDER BY 1, 3;
