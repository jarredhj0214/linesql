insert into ads.user_order_summary
temporary partition(tp202608)
with label load_user_order_summary_tp
(user_id, total_amount)
select user_id, sum(amount) as total_amount
from dwd.orders
where dt = '2026-08-24'
group by user_id;
