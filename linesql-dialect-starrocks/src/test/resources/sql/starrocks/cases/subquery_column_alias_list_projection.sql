select q.user_id, q.user_name
from (
  select id, name
  from ods.users
) as q(user_id, user_name);
