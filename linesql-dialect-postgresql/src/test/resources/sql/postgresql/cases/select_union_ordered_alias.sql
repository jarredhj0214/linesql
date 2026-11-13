SELECT SUM(a.amount) FILTER (WHERE a.active) AS total
FROM sales.orders AS a
UNION ALL
SELECT SUM(b.amount) FILTER (WHERE b.active) AS archived_total
FROM sales.orders_archive AS b
ORDER BY total DESC;
