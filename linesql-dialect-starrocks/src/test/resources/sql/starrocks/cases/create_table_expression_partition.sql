create table mart.event_daily (
  event_time datetime,
  user_id bigint,
  amount decimal(18, 2)
)
duplicate key(event_time, user_id)
partition by date_trunc('day', event_time)
distributed by hash(user_id) buckets 8
properties ("replication_num" = "1")
