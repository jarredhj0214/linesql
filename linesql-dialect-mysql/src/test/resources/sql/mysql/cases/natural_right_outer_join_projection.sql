select u.id, o.order_id
from app.users u
natural right outer join app.orders o
where o.status = 'PAID';
