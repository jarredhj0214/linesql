create table ods_users (
  id bigint,
  name string,
  ts timestamp(3),
  watermark for ts as ts - interval '5' second
) with (
  'connector' = 'kafka',
  'topic' = 'ods_users'
)
