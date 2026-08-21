select
  user_id,
  sum(amount) over (
    partition by user_id
    order by order_time
    rows between 6 preceding and current row
  ) as rolling_amount
from app.orders;
