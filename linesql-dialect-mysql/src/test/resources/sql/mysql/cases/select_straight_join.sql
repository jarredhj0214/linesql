select straight_join u.id as user_id, o.amount
from app.users u straight_join app.orders o on u.id = o.user_id
where o.status = 'PAID'
