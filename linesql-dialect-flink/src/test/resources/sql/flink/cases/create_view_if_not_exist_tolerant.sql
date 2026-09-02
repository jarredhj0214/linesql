CREATE VIEW IF NOT EXIST v_orders AS
SELECT id, amount
FROM dwd.orders
