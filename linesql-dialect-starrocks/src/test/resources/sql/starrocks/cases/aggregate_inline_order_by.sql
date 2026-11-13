SELECT o.user_id,
       ARRAY_AGG(o.sku ORDER BY o.created_at DESC NULLS LAST, o.id) AS ordered_skus
FROM dwd.orders AS o
GROUP BY o.user_id;
