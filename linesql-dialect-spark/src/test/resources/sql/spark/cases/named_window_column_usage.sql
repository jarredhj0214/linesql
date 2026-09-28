select
  user_id,
  sum(amount) over running_window as running_amount
from ods.orders
window
  running_window as (partition by user_id order by event_time rows between unbounded preceding and current row),
  unused_window as (partition by tenant_id)
