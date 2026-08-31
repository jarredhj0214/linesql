create table lake.order_events (
  order_id bigint,
  user_id bigint,
  amount decimal(18, 2),
  dt string
)
partitioned by (dt)
with (
  'connector' = 'filesystem',
  'path' = 's3://warehouse/order_events',
  'format' = 'parquet'
);
