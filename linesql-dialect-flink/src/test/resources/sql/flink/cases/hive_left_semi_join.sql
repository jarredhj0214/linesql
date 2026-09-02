SELECT o.order_id
FROM dwd.orders o
LEFT SEMI JOIN dim.products p
ON o.product_id = p.id
