create table mart.range_distributed_orders (
  order_id bigint,
  user_id bigint,
  order_time datetime,
  amount decimal(18, 2)
)
duplicate key(order_id, order_time)
order by (order_time, order_id)
properties (
  "replication_num" = "3"
);
