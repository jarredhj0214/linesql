SELECT region, channel, sum(amount) AS total_amount
FROM dwd.orders
GROUP BY region, channel WITH ROLLUP
