select q.user_id, q.name
from (
  select id as user_id, name
  from ods_users
) q;
