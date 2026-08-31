create materialized view mart.mv_order_daily
partition by date_trunc('day', dt)
distributed by hash(order_id) buckets 16
refresh async start('2026-08-01 00:00:00') every (interval 1 day)
properties ("replication_num" = "1")
as
select
  dt,
  order_id,
  user_id,
  sum(amount) as total_amount
from dwd.orders
group by dt, order_id, user_id
