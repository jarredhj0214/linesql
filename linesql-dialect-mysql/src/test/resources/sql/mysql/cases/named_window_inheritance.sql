select
  user_id,
  sum(amount) over w2 as running_amount
from app.orders
window
  w1 as (partition by user_id),
  w2 as (w1 order by created_at rows between unbounded preceding and current row);
