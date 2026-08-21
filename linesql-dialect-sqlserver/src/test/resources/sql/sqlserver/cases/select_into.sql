select top (100)
  u.id as user_id,
  u.name,
  o.amount as latest_amount
into dbo.active_user_orders
from dbo.users u
join dbo.orders o on u.id = o.user_id
where u.status = 'ACTIVE';
