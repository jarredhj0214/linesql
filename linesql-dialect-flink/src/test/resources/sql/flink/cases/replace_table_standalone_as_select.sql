REPLACE TABLE ads.order_amounts_v2 (
  PRIMARY KEY (user_id) NOT ENFORCED
)
DISTRIBUTED BY (user_id) INTO 4 BUCKETS
WITH (
  'connector' = 'print'
)
AS
SELECT user_id, sum(amount) AS total_amount
FROM dwd.orders
GROUP BY user_id
