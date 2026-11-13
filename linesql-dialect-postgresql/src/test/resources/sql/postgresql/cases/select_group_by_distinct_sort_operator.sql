SELECT o.region, o.channel, SUM(o.amount) AS total
FROM sales.orders AS o
GROUP BY DISTINCT GROUPING SETS (ROLLUP (o.region, o.channel), ())
ORDER BY total USING OPERATOR(pg_catalog.>) NULLS LAST;
