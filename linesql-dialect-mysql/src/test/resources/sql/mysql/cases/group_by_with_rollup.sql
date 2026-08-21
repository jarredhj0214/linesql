select region, count(order_id) as order_count
from app.orders
where status = 'PAID'
group by region with rollup
