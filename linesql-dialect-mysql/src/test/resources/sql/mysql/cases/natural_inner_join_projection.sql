select u.id, o.amount
from app.users u natural inner join app.orders o
where u.status = 'ACTIVE';
