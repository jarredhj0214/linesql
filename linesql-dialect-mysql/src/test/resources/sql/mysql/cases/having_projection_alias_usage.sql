select user_id, sum(amount) as total_amount
from app.orders
group by user_id
having total_amount > 1000;
