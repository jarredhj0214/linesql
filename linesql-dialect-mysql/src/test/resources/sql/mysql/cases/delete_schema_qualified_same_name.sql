DELETE live.orders FROM live.orders JOIN archive.orders
ON live.orders.id = archive.orders.id WHERE archive.orders.retired = 1;
