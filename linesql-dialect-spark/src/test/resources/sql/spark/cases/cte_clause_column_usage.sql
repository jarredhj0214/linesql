with recent_users as (
  select id, status
  from ods.users
  where dt = '${yyyy-MM-dd}'
)
select id
from recent_users
where status = 'active'
group by id
