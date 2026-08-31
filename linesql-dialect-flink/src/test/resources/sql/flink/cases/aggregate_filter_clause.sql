select
  user_id,
  count(*) filter (where status = 'paid') as paid_count,
  sum(amount) filter (where status = 'paid') as paid_amount
from dwd.orders
group by user_id;
