INSERT INTO mart.audit (order_id, max_flag)
SELECT o.id, (SELECT MAX(f.flag) FROM ods.flags f WHERE f.order_id = o.id)
FROM ods.orders o WHERE o.active = TRUE;
