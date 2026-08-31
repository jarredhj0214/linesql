with active_users as (
  select id as user_id, name as user_name
  from app.users
  where status = 'ACTIVE'
)
replace into mart.user_snapshot (user_id, user_name)
select user_id, user_name
from active_users;
