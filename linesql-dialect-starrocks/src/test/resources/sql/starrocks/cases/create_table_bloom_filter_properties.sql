create table mart.orders_bloom (
  user_id bigint,
  order_id bigint,
  amount decimal(18, 2)
)
duplicate key(user_id)
distributed by hash(user_id) buckets 8
properties ("bloom_filter_columns" = "user_id,order_id");
