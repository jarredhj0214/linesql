select
  customer_id,
  max(amount) keep (dense_rank last order by created_at) as latest_amount
from sales.orders
group by customer_id;
