select
  dt,
  region,
  grouping(region) as region_grouping,
  sum(amount) as total_amount
from dwd.orders
group by rollup(dt, region)
