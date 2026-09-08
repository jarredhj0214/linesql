select /*+ query_timeout(1000000) */
  id as user_id,
  name
from app.users
where status = 'ACTIVE'
