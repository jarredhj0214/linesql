SELECT region, channel, count(order_id) AS order_count
FROM dwd.orders
GROUP BY CUBE(region, channel)
HAVING count(order_id) > 0
