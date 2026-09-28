select
  user_id,
  row_number() over recent_window as event_rank
from ods.events
window
  user_partition as (partition by user_id),
  recent_window as (user_partition order by event_time rows between 3 preceding and current row),
  unused_window as (partition by tenant_id)
