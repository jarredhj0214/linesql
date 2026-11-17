SELECT live.orders.*, history.orders.*
FROM live.orders JOIN history.orders ON live.orders.id = history.orders.id;
