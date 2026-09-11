select
  id,
  isnull(updated_at, getdate()) as effective_ts,
  sysdatetime() as observed_at
from dbo.events
where deleted_at is null
  and archived_at is not null
  and retry_count = default
  and updated_at < getdate()
