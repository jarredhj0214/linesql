insert into mart.user_summary (user_id, user_name)
with active_users as (
  select id, name
  from app.users
  where status = 'ACTIVE'
)
select id, name
from active_users
