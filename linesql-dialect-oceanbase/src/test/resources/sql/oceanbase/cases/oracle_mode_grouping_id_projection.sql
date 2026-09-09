select
  o.region,
  o.product,
  grouping_id(o.region, o.product) as grouping_key,
  sum(o.amount) as total_amount
from sales.orders o
group by rollup(o.region, o.product);
