insert into ads.user_events with label load_20260821 (event_id, user_id)
select event_id, user_id
from ods.user_events_delta;
