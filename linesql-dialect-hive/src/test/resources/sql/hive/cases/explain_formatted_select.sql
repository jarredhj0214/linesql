explain formatted
select user_id, max(event_time) as last_event_time
from dwd.user_events
where dt = '2026-09-11'
group by user_id
