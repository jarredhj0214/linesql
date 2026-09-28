select u.id as user_id
from ods.users u
left semi join (
  select o.user_id
  from dwd.orders o
  where o.status = 'PAID'
) paid
  on u.id = paid.user_id
