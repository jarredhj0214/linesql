submit task scheduled_ctas
schedule start("2026-09-02 18:00:00") every(interval 1 hour)
as create table ads.user_order_count_hourly
as select
  date_trunc('hour', event_time) as event_hour,
  user_id,
  count(order_id) as order_count
from dwd.orders
group by date_trunc('hour', event_time), user_id;
