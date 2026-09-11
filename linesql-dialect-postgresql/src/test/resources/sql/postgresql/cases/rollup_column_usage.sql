SELECT region, channel, count(order_id) AS order_count
FROM sales.orders
GROUP BY ROLLUP(region, channel)
HAVING count(order_id) > 0
