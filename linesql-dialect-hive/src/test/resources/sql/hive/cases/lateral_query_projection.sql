select u.id, recent.amount
from ods.users u
join lateral (
  select o.amount
  from dwd.orders o
  where o.user_id = u.id
) recent on true;
