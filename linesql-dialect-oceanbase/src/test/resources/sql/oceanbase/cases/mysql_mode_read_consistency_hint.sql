select /*+ read_consistency(weak) */
  id as user_id,
  name
from app.users
where status = 'ACTIVE'
