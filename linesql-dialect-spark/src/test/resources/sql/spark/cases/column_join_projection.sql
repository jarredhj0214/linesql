select
  u.id as user_id,
  o.amount as order_amount
from ods.users u
join ods.orders o on u.id = o.user_id
