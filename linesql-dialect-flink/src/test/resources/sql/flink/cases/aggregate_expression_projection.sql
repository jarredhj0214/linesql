select
  o.user_id,
  count(o.order_id) as order_count,
  sum(o.amount) as total_amount
from ods_orders o
group by o.user_id;
