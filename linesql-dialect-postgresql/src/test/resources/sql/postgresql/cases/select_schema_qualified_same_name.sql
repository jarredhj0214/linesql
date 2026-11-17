SELECT live.orders.id AS live_id, historical.orders.id AS historical_id
FROM live.orders JOIN historical.orders ON live.orders.link_id = historical.orders.link_id
WHERE live.orders.status = 1;
