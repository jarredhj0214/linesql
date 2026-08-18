select u.name as user_name, o.amount as amount
from app.users u
join app.orders o on u.id = o.user_id
order by user_name, amount;
