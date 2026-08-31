select
  user_id,
  sum(amount) over (
    partition by user_id
    order by order_time
    range between interval 7 day preceding and current row
  ) as rolling_amount
from app.orders;
