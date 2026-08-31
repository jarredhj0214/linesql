select u.*, o.amount as order_amount
from app.users u
join app.orders o on u.id = o.user_id;
