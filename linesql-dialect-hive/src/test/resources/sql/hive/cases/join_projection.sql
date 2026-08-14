select u.id as user_id,
       o.amount
from ods.users u
join dwd.orders o on u.id = o.user_id
