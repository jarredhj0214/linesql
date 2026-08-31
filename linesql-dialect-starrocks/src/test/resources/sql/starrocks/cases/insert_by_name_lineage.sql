insert into ads.user_events by name
select event_id,
       user_id,
       event_time
from ods.user_events_delta
