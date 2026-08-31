select
  user_id,
  sum(amount) over (
    partition by user_id
    order by created_at
    rows unbounded preceding
  ) as running_amount
from app.orders;
