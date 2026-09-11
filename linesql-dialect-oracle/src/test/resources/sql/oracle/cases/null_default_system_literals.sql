select
  id,
  nvl(updated_at, sysdate) as effective_ts,
  current_timestamp as observed_at
from app.events
where deleted_at is null
  and archived_at is not null
  and retry_count = default
  and created_at < current_date
