SELECT order_id
FROM (mart.orders o RIGHT JOIN staging.stock s USING (order_id))
LEFT JOIN archive.stock h USING (order_id);
