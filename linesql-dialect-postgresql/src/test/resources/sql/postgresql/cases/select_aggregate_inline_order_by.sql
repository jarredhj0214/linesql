SELECT o.customer_id,
       string_agg(o.sku, ',' ORDER BY o.created_at DESC) AS ordered_skus
FROM sales.orders AS o
GROUP BY o.customer_id;
