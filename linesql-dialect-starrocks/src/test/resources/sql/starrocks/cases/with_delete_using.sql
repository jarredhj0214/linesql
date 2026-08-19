with stale_users as (
  select user_id
  from dwd.orders
  where status = 'CANCELLED'
)
delete from ads.sessions s
using stale_users u
where s.user_id = u.user_id;
