with order_amounts as (
  select user_id, amount
  from dbo.orders
  where status = 'PAID'
)
update dbo.users
set total_amount = o.amount
from dbo.users u
join order_amounts o on u.id = o.user_id
where u.status = 'ACTIVE';
