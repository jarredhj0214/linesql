insert into table ads.user_summary (user_id, user_name)
select user_id, name
from (
  select id as user_id, name
  from ods.users
) u
