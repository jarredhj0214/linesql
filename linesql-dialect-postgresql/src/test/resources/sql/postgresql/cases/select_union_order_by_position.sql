SELECT a.id, a.amount FROM sales.orders AS a
UNION ALL
SELECT b.id, b.amount FROM sales.orders_archive AS b
ORDER BY 2 DESC;
