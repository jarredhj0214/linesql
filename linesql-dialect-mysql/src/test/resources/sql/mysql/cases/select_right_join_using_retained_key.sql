SELECT o.order_id AS order_id, order_id AS merged_id
FROM mart.orders o RIGHT JOIN staging.stock s USING (order_id)
WHERE order_id > 0
ORDER BY order_id;
