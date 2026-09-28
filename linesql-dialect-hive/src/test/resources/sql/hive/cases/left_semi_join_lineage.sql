select u.id as user_id, u.name
from ods.users u
left semi join dwd.orders o
  on u.id = o.user_id and o.status = 'PAID'
