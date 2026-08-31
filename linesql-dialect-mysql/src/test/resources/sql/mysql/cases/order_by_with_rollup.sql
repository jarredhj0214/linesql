select region, sum(amount) as total_amount
from app.orders
where status = 'PAID'
group by region
order by total_amount desc with rollup
