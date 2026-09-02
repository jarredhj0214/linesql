CREATE TABLE dwd.reordered_orders (
  order_time,
  price,
  quantity,
  order_id
)
WITH ('connector' = 'kafka')
AS SELECT order_id, price, quantity, order_time
FROM ods.orders
