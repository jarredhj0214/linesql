INSERT INTO dwd.product_orders
SELECT p.name, o.order_id
FROM ods.orders o
JOIN dim.products p ON o.product_name = p.name
ON CONFLICT DO NOTHING
