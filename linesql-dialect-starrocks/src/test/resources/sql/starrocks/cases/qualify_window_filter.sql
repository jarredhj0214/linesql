select user_id, event_time
from dwd.user_events
qualify row_number() over(partition by user_id order by event_time desc) = 1;
