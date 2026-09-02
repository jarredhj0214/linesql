SELECT order_id, user_id, product_id
FROM ods.orders
QUALIFY ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY proctime ASC) = 1
