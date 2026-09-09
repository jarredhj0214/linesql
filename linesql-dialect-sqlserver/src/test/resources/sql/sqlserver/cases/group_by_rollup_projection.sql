select
  o.region,
  o.product,
  sum(o.amount) as total_amount
from sales.orders o
group by rollup(o.region, o.product);
