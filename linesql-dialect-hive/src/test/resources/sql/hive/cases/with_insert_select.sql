with q as (
  select id as user_id, name
  from ods.users
  where active = true
)
insert into table ads.user_summary (user_id, user_name)
select user_id, name
from q;
