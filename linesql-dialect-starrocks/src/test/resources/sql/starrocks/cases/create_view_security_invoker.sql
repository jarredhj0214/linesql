CREATE VIEW mart.secure_orders (order_key COMMENT 'stable order key')
COMMENT 'invoker view' SECURITY INVOKER AS
SELECT order_id FROM ods.orders WHERE active = TRUE
