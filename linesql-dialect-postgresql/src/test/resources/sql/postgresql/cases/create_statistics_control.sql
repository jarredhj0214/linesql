CREATE STATISTICS mart.orders_stats (dependencies, ndistinct)
ON customer_id, status, region
FROM mart.orders;
