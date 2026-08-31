select u.id, o.amount
from app.users u
join app.orders o on u.id = o.user_id
where u.status = 'ACTIVE'
for share of u, o skip locked;
