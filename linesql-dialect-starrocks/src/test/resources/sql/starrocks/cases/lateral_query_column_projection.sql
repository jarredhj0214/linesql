select q.user_id
from lateral (
  select id as user_id
  from ods.users
) q;
