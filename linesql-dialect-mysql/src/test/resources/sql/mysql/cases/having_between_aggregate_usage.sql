select region, sum(amount) as total_amount
from app.orders
group by region
having sum(amount) between min_amount and max_amount
