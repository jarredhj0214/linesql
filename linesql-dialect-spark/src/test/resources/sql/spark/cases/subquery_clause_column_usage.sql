select u.id
from (
  select id, status, dt
  from ods.users
  where dt = '${yyyy-MM-dd}'
  group by id, status, dt
) u
where u.status = 'active'
group by u.id
