UPDATE live.orders SET status = historical.orders.status
FROM historical.orders
WHERE live.orders.id = historical.orders.id
RETURNING live.orders.*, historical.orders.id AS historical_id;
