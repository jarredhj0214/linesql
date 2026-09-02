select
  user_id,
  row_number() over(partition by user_id order by event_time desc) as rn
from dwd.events
qualify rn = 1;
