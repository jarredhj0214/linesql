INSERT INTO mart.order_status (joined_id, status)
SELECT order_id, o.status
FROM ods.orders o LEFT JOIN ods.shipments s USING (order_id);
