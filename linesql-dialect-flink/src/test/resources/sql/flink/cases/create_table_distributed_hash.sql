create table dwd.orders_bucketed (
  order_id bigint not null,
  user_id bigint,
  amount decimal(18, 2)
)
distributed by hash(order_id) into 8 buckets
with (
  'connector' = 'filesystem',
  'path' = '/warehouse/dwd/orders'
);
