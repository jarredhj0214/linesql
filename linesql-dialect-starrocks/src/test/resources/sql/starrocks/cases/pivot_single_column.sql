select *
from dwd.sales
pivot (
  sum(amount) as total_amount,
  avg(quantity) as avg_quantity
  for region in ('north', 'south')
);
