select
  user_id,
  sum(amount) over w as running_amount
from app.orders
window w as (partition by user_id order by order_time);
