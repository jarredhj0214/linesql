select u.profile.city as city, o.metrics.amount as amount
from ods.users u
join ods.orders o on u.id = o.user_id
