alter table ods.orders add (
  log_ts string comment 'raw event time' first,
  rowtime as to_timestamp(log_ts) after log_ts,
  primary key (order_id) not enforced,
  watermark for rowtime as rowtime - interval '3' second
);
