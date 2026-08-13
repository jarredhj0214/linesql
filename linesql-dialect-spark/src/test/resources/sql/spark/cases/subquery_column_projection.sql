select user_id, name
from (
  select id as user_id, name
  from ods.users
) u
