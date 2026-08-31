select
  user_id,
  nth_value(amount, 2) from last ignore nulls over (
    partition by user_id
    order by created_at
    rows between unbounded preceding and unbounded following
  ) as second_last_amount
from app.orders
