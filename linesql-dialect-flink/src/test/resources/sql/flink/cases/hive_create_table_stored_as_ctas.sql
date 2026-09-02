CREATE TABLE ads.daily_order_amount
STORED AS parquet
TBLPROPERTIES ('sink.partition-commit.trigger' = 'partition-time')
AS
SELECT order_id, amount
FROM dwd.orders
