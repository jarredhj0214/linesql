SELECT live.orders.*, archive.orders.*
FROM live.orders JOIN archive.orders ON live.orders.id = archive.orders.id;
