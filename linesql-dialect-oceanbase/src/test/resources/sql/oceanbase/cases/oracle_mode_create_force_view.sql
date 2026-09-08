CREATE OR REPLACE FORCE VIEW mart.v_order_amount AS
SELECT order_id, amount
FROM ods.orders;
