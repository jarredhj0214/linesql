with q as (
  select id as user_id, name
  from ods_users
  where active = true
)
insert into ads_user_summary (user_id, user_name)
select user_id, name
from q;
