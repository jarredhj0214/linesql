insert /*+ set_var(dynamic_overwrite = true) */ overwrite ads.order_summary
select user_id, count(order_id) as order_count
from dwd.orders
group by user_id
