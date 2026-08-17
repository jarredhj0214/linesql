select
  user_id,
  row_number() over (partition by region order by created_at) as rn,
  sum(amount) over (partition by user_id order by created_at) as running_amount
from ods.orders;
