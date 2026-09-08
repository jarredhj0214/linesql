select north_amt, south_amt
from (
  select region, amount
  from sales.orders
)
pivot (
  sum(amount) as amt for region in ('north' as north, 'south' as south)
)
