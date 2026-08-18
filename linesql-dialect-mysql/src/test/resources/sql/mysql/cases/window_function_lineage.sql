select user_id,
       sum(amount) over(partition by user_id order by order_time) as running_amount,
       row_number() over(partition by region order by order_time) as rn
from app.orders;
