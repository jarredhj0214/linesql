FROM ods.orders o FULL OUTER JOIN ods.shipments s USING (order_id)
INSERT INTO mart.order_status (joined_id, status) SELECT order_id, o.status WHERE order_id > 0
INSERT INTO mart.shipping_cost (joined_id, cost) SELECT order_id, s.cost WHERE s.cost IS NOT NULL;
