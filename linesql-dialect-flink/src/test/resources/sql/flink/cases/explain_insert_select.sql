explain insert into ads.order_amounts
select user_id, sum(amount) as total_amount
from dwd.orders
group by user_id;
