SELECT order_id, s.shipping_cost
FROM ods.orders o RIGHT JOIN ods.shipments s USING (order_id)
ORDER BY order_id;
