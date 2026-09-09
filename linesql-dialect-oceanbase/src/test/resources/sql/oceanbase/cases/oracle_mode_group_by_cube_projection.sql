select
  o.region,
  o.product,
  sum(o.amount) as total_amount
from sales.orders o
group by cube(o.region, o.product);
