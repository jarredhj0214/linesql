select id, created_at
from app.events
where created_at >= date '2026-09-01'
  and updated_at < timestamp '2026-09-02 00:00:00';
