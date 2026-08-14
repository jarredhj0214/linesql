create table ads.user_metrics (
  user_id bigint,
  event_day date,
  pv bigint sum,
  max_score decimal(18, 2) max
)
aggregate key(user_id, event_day)
distributed by hash(user_id) buckets 16
properties ("replication_num" = "1");
