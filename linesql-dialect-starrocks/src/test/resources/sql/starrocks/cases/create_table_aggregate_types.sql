create table mart.user_metrics (
  user_id bigint,
  pv bigint sum,
  uv hll hll_union,
  tags bitmap bitmap_union,
  last_city varchar(64) replace_if_not_null
)
aggregate key(user_id)
distributed by hash(user_id)
properties ("replication_num" = "3");
