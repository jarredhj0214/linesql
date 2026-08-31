select
  cast(event_ts at time zone timezone_name as datetime) as local_event_ts,
  cast(created_at at time zone '+00:00' as datetime(3)) as utc_created_at
from app.events
where cast(updated_at at time zone timezone_name as datetime) >= timestamp '2026-01-01 00:00:00'

