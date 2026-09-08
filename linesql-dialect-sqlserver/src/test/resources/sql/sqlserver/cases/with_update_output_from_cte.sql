with paid_orders as (
  select user_id, amount
  from dbo.orders
  where status = 'PAID'
)
update dbo.users
set total_amount = p.amount
output inserted.id, inserted.total_amount
into audit.user_change_log (user_id, total_amount)
from dbo.users u
join paid_orders p on u.id = p.user_id
where u.status = 'ACTIVE';
