select u.id, u.name
from ods.users u
where exists (
  select 1
  from ods.orders o
  where o.user_id = u.id
    and o.status = 'PAID'
);
