REPLACE INTO mart.audit (order_id, max_adjustment)
SELECT o.id, (SELECT MAX(a.amount) FROM ods.adjustments a WHERE a.order_id = o.id)
FROM ods.orders o WHERE o.active = 1;
