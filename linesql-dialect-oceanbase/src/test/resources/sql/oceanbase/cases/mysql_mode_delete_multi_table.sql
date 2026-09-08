delete u, o
from mart.users u
join app.orders o on u.id = o.user_id
where o.expired = 1
