load label mart.load_order_events_202608 (
  data infile ("s3://bucket/orders/dt=2026-08-24/*.parquet")
  negative
  into table ods.order_events
  temporary partition (p202608)
  columns terminated by "|"
  rows terminated by "\n"
  format as "parquet"
  ("trim_space" = "true")
  (order_id, user_id, amount)
  columns from path as (dt)
  set load_date = str_to_date(dt, '%Y-%m-%d')
  where order_id is not null
)
with broker (
  "aws.s3.region" = "cn-north-1"
)
properties (
  "timeout" = "3600"
);
