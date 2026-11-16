SELECT o.customer_id, lower(o.channel) AS channel_key, sum(o.amount) AS total
FROM sales.orders o
GROUP BY GROUPING SETS ((1, 2), (1), ())
ORDER BY 2;
