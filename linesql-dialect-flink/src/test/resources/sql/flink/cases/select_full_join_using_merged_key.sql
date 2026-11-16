SELECT order_id, o.status, s.shipping_cost
FROM ods.orders o FULL OUTER JOIN ods.shipments s USING (order_id)
WHERE order_id > 0;
