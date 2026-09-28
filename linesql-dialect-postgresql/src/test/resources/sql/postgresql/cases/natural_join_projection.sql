select u.id as user_id, o.amount
from app.users u
natural join sales.orders o
where u.status = 'ACTIVE'
