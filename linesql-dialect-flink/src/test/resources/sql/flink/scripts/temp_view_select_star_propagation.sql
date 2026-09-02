CREATE TEMPORARY VIEW v_orders AS
SELECT id, amount
FROM ods.orders;

INSERT INTO ads.orders_copy
SELECT *
FROM v_orders;
