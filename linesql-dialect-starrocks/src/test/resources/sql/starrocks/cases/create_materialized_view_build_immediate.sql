create materialized view mart.mv_daily_orders
build immediate
refresh async every(interval 1 day)
partition by date_trunc('day', order_time)
distributed by hash(user_id) buckets 8
properties ("replication_num" = "3")
as
select
  user_id,
  date_trunc('day', order_time) as order_day,
  sum(amount) as amount
from dwd.orders
group by user_id, date_trunc('day', order_time);
