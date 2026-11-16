UPDATE mart.orders o
JOIN (SELECT s.source_key, s.quantity FROM staging.stock s) AS stock(order_id, quantity)
USING (order_id)
SET o.quantity = stock.quantity
WHERE stock.quantity > 0;
