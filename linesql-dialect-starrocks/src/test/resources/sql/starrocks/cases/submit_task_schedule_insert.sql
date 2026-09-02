submit task
schedule every(interval 1 minute)
as insert overwrite ads.user_order_count_daily
select
  dt,
  user_id,
  count(order_id) as order_count
from dwd.orders
group by dt, user_id;
