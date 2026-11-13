SELECT ARRAY_AGG(a.sku ORDER BY a.created_at) AS items
FROM dwd.orders AS a
UNION ALL
SELECT ARRAY_AGG(b.sku ORDER BY b.created_at) AS archived_items
FROM dwd.orders_archive AS b
ORDER BY items;
