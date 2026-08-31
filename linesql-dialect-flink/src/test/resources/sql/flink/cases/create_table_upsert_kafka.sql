create table ads.user_summary_sink (
  user_id bigint,
  order_count bigint,
  total_amount decimal(18, 2),
  primary key (user_id) not enforced
) with (
  'connector' = 'upsert-kafka',
  'topic' = 'user-summary',
  'key.format' = 'json',
  'value.format' = 'json'
);
