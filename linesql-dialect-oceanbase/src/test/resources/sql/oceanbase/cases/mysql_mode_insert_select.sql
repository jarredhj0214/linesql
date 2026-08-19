insert into mart.user_orders(user_id, amount)
select u.id, o.amount
from app.users u
join app.orders o on u.id = o.user_id
