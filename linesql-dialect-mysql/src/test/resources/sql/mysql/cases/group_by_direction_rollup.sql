select region, count(order_id) as order_count
from app.orders
group by region desc with rollup;
