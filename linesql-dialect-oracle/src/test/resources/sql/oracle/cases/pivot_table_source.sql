select *
from (
  select region, amount
  from sales.orders
)
pivot (
  sum(amount) for region in ('north' as north_amt, 'south' as south_amt)
)
