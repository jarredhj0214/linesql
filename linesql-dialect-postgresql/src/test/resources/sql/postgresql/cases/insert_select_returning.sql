insert into mart.user_orders(user_id, amount)
select u.id, o.amount
from public.users u
join sales.orders o on u.id = o.user_id
returning user_id
