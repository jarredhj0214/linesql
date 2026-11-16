INSERT INTO mart.reconciled (merged_id, active_flag)
SELECT order_id, f.active
FROM (ods.orders o FULL JOIN ods.shipments s USING (order_id))
LEFT JOIN ods.flags f USING (order_id);
