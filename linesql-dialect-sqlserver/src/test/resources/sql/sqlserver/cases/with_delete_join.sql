with stale_users as (
  select user_id
  from dbo.orders
  where status = 'CANCELLED'
)
delete s
from dbo.sessions s
join stale_users u on s.user_id = u.user_id
where s.expired = 1;
