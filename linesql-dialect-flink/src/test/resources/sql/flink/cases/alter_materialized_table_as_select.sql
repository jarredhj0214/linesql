ALTER MATERIALIZED TABLE dwd.mv_user_orders
AS SELECT user_id, COUNT(order_id) AS order_count
FROM dwd.orders
GROUP BY user_id
