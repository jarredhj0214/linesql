CREATE MATERIALIZED TABLE dwd.mv_user_orders (
  user_id BIGINT,
  order_count BIGINT,
  PRIMARY KEY (user_id) NOT ENFORCED
)
DISTRIBUTED BY RANGE (user_id) INTO 8 BUCKETS
PARTITIONED BY (dt)
WITH ('format' = 'json')
FRESHNESS = INTERVAL '1' HOUR
REFRESH_MODE = FULL
AS SELECT user_id, COUNT(order_id) AS order_count, dt
FROM dwd.orders
GROUP BY user_id, dt
