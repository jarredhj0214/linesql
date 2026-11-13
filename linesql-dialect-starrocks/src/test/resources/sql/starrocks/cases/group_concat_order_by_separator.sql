SELECT o.user_id,
       GROUP_CONCAT(DISTINCT o.sku ORDER BY o.created_at DESC SEPARATOR '|') AS ordered_skus
FROM dwd.orders AS o
GROUP BY o.user_id;
