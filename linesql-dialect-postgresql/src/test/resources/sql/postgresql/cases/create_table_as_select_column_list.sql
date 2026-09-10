create table mart.user_order_snapshot(user_id, order_amount) as
select u.id, o.amount
from public.users u
join sales.orders o on u.id = o.user_id
where u.status = 'ACTIVE'
with data;
