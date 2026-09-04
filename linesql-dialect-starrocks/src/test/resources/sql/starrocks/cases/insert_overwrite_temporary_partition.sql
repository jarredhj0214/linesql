insert overwrite table ads.user_order_summary temporary partition(tp202608)
  (user_id, total_amount)
properties ("strict_mode" = "true")
select user_id, sum(amount) as total_amount
from dwd.orders
where dt = '2026-08-21'
group by user_id;
