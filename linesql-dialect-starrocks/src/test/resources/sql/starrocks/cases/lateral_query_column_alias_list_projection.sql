select q.user_id
from lateral (
  select id
  from ods.users
) as q(user_id);
