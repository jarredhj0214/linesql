create table mart.order_hash (
  id number,
  amount number
)
partition by hash (id) partitions 8
