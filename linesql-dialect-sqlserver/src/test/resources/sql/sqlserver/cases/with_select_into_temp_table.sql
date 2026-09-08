with active_users as (
  select id, name
  from dbo.users
  where status = 'ACTIVE'
)
select id as user_id, name
into #active_users
from active_users;
