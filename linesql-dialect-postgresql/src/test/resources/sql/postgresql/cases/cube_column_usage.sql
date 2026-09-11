SELECT region, channel, sum(amount) AS total_amount
FROM sales.orders
GROUP BY CUBE(region, channel)
