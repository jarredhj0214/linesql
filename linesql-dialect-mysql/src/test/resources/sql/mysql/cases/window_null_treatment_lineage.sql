select
  user_id,
  first_value(amount) respect nulls over (
    partition by user_id
    order by created_at
    rows between unbounded preceding and current row
  ) as first_amount
from app.orders

