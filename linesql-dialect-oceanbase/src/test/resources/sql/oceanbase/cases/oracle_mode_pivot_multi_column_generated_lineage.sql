select north_app_total
from (
  select region, channel, amount
  from sales.orders
)
pivot (
  sum(amount) as total
  for (region, channel) in (('north', 'app') as north_app)
)
