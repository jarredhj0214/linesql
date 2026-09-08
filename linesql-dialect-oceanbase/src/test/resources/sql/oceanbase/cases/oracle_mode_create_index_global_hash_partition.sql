create unique index mart.idx_orders_id on mart.orders (id)
global partition by hash (id) partitions 8
