select
  o.region,
  o.product,
  sum(o.amount) as total_amount
from sales.orders o
group by grouping sets ((o.region, o.product), (o.region), ());
