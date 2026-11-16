INSERT OVERWRITE TABLE mart.audit (order_id, max_flag)
SELECT o.id, (SELECT MAX(f.flag) FROM ods.flags f WHERE f.active = TRUE)
FROM ods.orders o WHERE o.active = TRUE;
