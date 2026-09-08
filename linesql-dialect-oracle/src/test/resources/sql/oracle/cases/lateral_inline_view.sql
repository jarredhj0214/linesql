select u.id, o.latest_amount
from ods.users u
cross join lateral (
  select max(o.amount) as latest_amount
  from ods.orders o
  where o.user_id = u.id
) o
where u.status = 'ACTIVE';
