SELECT o.order_id, lv.sku
FROM dwd.orders o
LATERAL VIEW explode(o.items) lv AS sku
