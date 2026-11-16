INSERT INTO mart.audit (bucket, net_amount, order_id)
SELECT 1, o.amount - o.discount, o.id FROM ods.orders o WHERE o.active = 1;
