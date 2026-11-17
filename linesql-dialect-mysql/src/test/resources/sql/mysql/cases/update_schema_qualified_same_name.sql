UPDATE live.orders JOIN archive.orders ON live.orders.id = archive.orders.id
SET live.orders.total = archive.orders.amount WHERE live.orders.active = 1;
