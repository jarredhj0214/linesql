SELECT o.id
FROM dbo.orders AS o WITH (INDEX(ix_orders_status), READPAST)
WHERE o.status = 1;
