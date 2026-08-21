create materialized view mart.mv_user_events
distributed by hash(user_id)
refresh async
as
select user_id, count(event_id) as event_count
from dwd.user_events
group by user_id;
