select
  o.region,
  count(distinct o.user_id) as user_count,
  sum(distinct o.amount) as distinct_amount
from ods.orders o
group by o.region
having count(distinct o.order_id) > 1;
