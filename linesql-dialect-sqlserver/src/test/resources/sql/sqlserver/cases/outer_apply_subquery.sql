select u.id as user_id, q.last_status
from dbo.users u
outer apply (
  select top 1 o.status as last_status
  from dbo.orders o
  where o.user_id = u.id
  order by o.created_at desc
) q;
