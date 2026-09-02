SELECT o.order_id
FROM dwd.orders o
LEFT ANTI JOIN dim.blacklist b
ON o.user_id = b.user_id
