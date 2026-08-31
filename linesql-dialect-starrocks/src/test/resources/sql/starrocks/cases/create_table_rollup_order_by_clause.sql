create table mart.order_metrics_sorted (
  dt date,
  user_id bigint,
  amount decimal(18, 2)
)
duplicate key(dt, user_id)
distributed by hash(user_id) buckets 16
rollup (
  r_user(user_id, amount) from order_metrics_sorted properties ("timeout" = "3600")
)
order by (dt, user_id)
properties ("replication_num" = "3")
