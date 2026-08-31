create table mart.city_orders (
  dt date,
  city varchar(64),
  order_id bigint,
  amount decimal(18, 2)
)
duplicate key(dt, city, order_id)
partition by (dt, city)
distributed by hash(order_id) buckets 8
properties ("replication_num" = "1")
