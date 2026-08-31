create table mart.order_metrics (
  dt date,
  user_id bigint,
  amount decimal(18, 2)
)
duplicate key(dt, user_id)
distributed by hash(user_id) buckets 16
rollup (
  r_user(user_id, amount),
  r_dt(dt, amount) from order_metrics properties ("timeout" = "3600")
)
properties ("replication_num" = "3")
