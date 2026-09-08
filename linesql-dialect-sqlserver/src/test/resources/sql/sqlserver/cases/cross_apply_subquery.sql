select u.id as user_id, q.last_amount
from dbo.users u
cross apply (
  select top 1 o.amount as last_amount
  from dbo.orders o
  where o.user_id = u.id
  order by o.created_at desc
) q;
