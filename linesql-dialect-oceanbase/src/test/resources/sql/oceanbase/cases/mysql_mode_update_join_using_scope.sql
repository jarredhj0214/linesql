UPDATE mart.orders o
JOIN staging.stock s USING (order_id)
SET o.quantity = s.quantity
WHERE s.active = 1;
