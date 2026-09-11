explain extended
insert overwrite table ads.user_event_daily
select user_id, event_time
from dwd.user_events
where dt = '2026-09-11'
