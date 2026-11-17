UPDATE live.orders SET total = archive.orders.amount
FROM archive.orders
WHERE live.orders.id = archive.orders.id;
