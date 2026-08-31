select u.id, recent.last_amount
from app.users u
join lateral (
  select o.amount as last_amount
  from app.orders o
  where o.user_id = u.id
  order by o.created_at desc
  limit 1
) recent on true;
