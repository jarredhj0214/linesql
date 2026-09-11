select u.id, recent.last_amount
from app.users u
join lateral (
  select o.amount, o.created_at
  from app.orders o
  where o.user_id = u.id
  order by o.created_at desc
  limit 1
) as recent(last_amount, last_order_time) on true;
