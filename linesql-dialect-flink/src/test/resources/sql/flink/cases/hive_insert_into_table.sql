INSERT INTO TABLE ads.order_sink
SELECT order_id, amount
FROM dwd.orders
