INSERT OVERWRITE DIRECTORY '/warehouse/export/orders' USING parquet
SELECT order_id, amount
FROM dwd.orders
