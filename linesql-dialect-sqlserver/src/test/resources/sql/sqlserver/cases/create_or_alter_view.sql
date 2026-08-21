create or alter view dbo.v_active_user_orders(user_id, latest_amount) as
select u.id, o.amount
from dbo.users u
join dbo.orders o on u.id = o.user_id
where u.status = 'ACTIVE';
