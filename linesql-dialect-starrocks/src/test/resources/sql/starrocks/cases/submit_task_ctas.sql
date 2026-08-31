submit task
as create table ads.user_order_count_daily
as select
  dt,
  user_id,
  count(order_id) as order_count
from dwd.orders
group by dt, user_id
