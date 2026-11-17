WITH staged AS (
    SELECT o.qty, o.total
    FROM sales.orders AS o
)
SELECT SUM(s.total) AS qty
FROM staged AS s
WHERE qty > 0
GROUP BY qty
HAVING qty > 10
ORDER BY qty + 1, qty;
