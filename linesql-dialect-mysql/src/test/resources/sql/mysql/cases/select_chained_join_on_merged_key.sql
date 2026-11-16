SELECT order_id, l.detail
FROM mart.orders o RIGHT JOIN mart.shipments s USING (order_id)
JOIN mart.lookup l ON order_id = l.lookup_key;
