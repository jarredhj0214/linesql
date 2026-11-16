SELECT order_id, l.label
FROM ods.orders o FULL OUTER JOIN ods.shipments s USING (order_id)
JOIN ods.lookup l ON order_id = l.source_key
WHERE order_id > 0;
