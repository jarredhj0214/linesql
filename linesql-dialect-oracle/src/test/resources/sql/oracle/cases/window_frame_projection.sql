select
  customer_id,
  sum(amount) over (
    partition by customer_id
    order by created_at
    rows between unbounded preceding and current row
  ) as running_amount
from sales.orders;
