SELECT item
FROM ods.orders
LATERAL VIEW explode(items) e AS Item
