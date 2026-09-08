create table mart.orders_by_tg (
  id bigint not null,
  user_id bigint,
  amount decimal(18, 2)
) tablegroup = tg_orders
partition by hash(user_id) partitions 16
