create table ods.kafka_orders (
  order_id bigint,
  payload string,
  kafka_ts timestamp_ltz(3) metadata from 'timestamp' virtual,
  rowtime as to_timestamp_ltz(kafka_ts, 3),
  watermark for rowtime as rowtime - interval '5' second,
  primary key (order_id) not enforced
)
comment 'kafka order source'
partitioned by (order_id)
with (
  'connector' = 'kafka',
  'topic' = 'orders'
);
