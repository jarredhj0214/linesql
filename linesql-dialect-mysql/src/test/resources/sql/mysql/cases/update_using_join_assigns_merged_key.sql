UPDATE mart.orders o RIGHT JOIN staging.stock s USING (order_id)
SET o.imported_id = order_id
WHERE order_id > 0;
