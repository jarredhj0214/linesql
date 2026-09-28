select sum(o.amount) over (
  partition by o.user_id
  order by o.event_time
  rows between unbounded preceding and current row
) as running_amount
from ods.orders o
