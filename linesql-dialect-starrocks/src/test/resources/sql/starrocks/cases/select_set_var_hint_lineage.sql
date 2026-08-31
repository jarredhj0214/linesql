select /*+ set_var(query_timeout = 7200) */
  user_id,
  count(order_id) as order_count
from dwd.orders
group by user_id
