create table ods.user_dim (
  user_id bigint primary key not enforced,
  user_name string,
  update_time timestamp_ltz(3)
)
with (
  'connector' = 'upsert-kafka',
  'topic' = 'user_dim',
  'key.format' = 'json',
  'value.format' = 'json'
);
