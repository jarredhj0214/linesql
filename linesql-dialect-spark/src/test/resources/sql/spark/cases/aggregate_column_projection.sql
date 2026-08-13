select user_id, count(order_id) as order_cnt, sum(amount) as total_amount
from ods.orders
group by user_id
