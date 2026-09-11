select
  region,
  channel,
  sum(amount) as total_amount
from app.orders
where deleted = 0
group by rollup(region, channel)
having sum(amount) > 100
