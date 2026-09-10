create index mart.idx_orders_status_dt
on mart.orders (status, trunc(created_at))
tablespace users
online
nologging
parallel 4
compress 1;
