insert into ads.user_events by name
properties ("timeout" = "120", "strict_mode" = "true")
select event_id,
       user_id,
       event_time
from ods.user_events_delta
where event_time >= '2026-08-01';
