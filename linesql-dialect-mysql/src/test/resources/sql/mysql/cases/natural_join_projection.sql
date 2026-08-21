select u.id as user_id, o.amount
from app.users u natural join app.orders o
where u.status = 'ACTIVE';
