select q.user_id, q.user_name
from (
  select id as user_id, name as user_name
  from app.users
) q
