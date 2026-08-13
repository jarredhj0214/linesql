create streaming table mart.streaming_events
as
select id as event_id, event_time
from stream(ods.events) s
