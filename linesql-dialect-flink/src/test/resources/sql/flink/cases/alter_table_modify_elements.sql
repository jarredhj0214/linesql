alter table ods.orders modify (
  log_ts string comment 'raw event time' after order_id,
  rowtime as to_timestamp(log_ts) after log_ts,
  primary key (order_id) not enforced,
  watermark for rowtime as rowtime
);
