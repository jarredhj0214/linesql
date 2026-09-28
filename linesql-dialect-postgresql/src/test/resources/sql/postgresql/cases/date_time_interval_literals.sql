select e.id,
       e.scheduled_at + interval '1 day' as next_run
from app.events e
where e.event_date = date '2026-09-28'
  and e.created_at < timestamp '2026-09-28 18:00:00'
  and e.start_time >= time '09:00:00';
