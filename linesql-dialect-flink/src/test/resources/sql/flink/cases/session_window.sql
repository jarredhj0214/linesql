select window_start, window_end, user_id, count(order_id) as order_count
from table(
  session(table dwd.orders, descriptor(rowtime), interval '30' minute)
)
group by window_start, window_end, user_id;
