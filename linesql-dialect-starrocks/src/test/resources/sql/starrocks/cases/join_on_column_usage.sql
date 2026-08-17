select u.id, o.amount
from ods.users u
join dwd.orders o on u.id = o.user_id and o.dt = '2026-08-17'
