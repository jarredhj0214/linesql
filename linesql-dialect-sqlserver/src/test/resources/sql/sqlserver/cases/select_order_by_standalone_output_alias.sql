SELECT SUM(o.total) AS qty
FROM sales.orders AS o
WHERE qty > 0
GROUP BY qty
HAVING qty > 10
ORDER BY qty + 1, qty;
