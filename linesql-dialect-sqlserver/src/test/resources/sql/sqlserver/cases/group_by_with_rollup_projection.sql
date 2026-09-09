select
  o.region,
  o.product,
  sum(o.amount) as total_amount
from sales.orders o
group by o.region, o.product with rollup;
