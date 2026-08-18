select u.id, u.name
from dbo.users u
where exists (
  select 1
  from dbo.orders o
  where o.user_id = u.id
    and o.status = 'PAID'
);
