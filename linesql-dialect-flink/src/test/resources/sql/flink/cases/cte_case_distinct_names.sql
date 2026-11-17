WITH Orders AS (SELECT id, amount FROM ods.ActiveOrders),
     orders AS (SELECT id, amount FROM ods.OrderHistory)
SELECT Orders.id AS active_id, orders.amount AS history_amount
FROM Orders JOIN orders ON Orders.id = orders.id
WHERE Orders.amount > 0
ORDER BY orders.amount;
