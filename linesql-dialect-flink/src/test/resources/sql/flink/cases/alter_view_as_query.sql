alter view ads.v_order_amounts as
select user_id, sum(amount) as total_amount
from dwd.orders
group by user_id;
