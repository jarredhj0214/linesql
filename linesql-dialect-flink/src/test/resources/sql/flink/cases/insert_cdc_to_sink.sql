insert into ads.user_summary_sink (user_id, order_count, total_amount)
select user_id, count(order_id) as order_count, sum(amount) as total_amount
from ods.mysql_orders
group by user_id;
