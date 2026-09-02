CREATE EXTERNAL TABLE IF NOT EXISTS ods.raw_orders (
  order_id BIGINT COMMENT 'order id',
  amount DECIMAL(18, 2)
)
COMMENT 'raw orders'
PARTITIONED BY (dt STRING COMMENT 'partition date')
STORED AS parquet
LOCATION '/user/hive/warehouse/ods/raw_orders'
TBLPROPERTIES ('sink.partition-commit.policy.kind' = 'metastore,success-file')
