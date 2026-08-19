with stale_users as (
  select user_id
  from app.orders
  where status = 'CANCELLED'
)
delete s
from app.sessions s
join stale_users u on s.user_id = u.user_id
where s.expired = 1;
