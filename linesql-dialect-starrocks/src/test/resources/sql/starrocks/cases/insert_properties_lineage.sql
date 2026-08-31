insert into ads.user_events
properties ("timeout" = "120", "strict_mode" = "true")
select event_id,
       user_id,
       channel
from ods.user_events_delta
