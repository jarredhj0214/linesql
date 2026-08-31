select u.id as user_id, o.amount
from app.users u natural left outer join app.orders o
where u.status = 'ACTIVE';
