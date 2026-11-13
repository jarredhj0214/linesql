PREPARE recent_orders FROM
SELECT o.id, o.amount FROM dwd.orders AS o WHERE o.tenant_id = ?;
