create view mart.v_user_orders as
select u.id as user_id, o.amount
from public.users u
join sales.orders o on u.id = o.user_id
