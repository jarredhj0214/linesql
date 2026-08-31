select window_start, window_end, user_id, count(order_id) as order_count
from table(
  cumulate(table dwd.orders, descriptor(rowtime), interval '10' minute, interval '1' hour)
)
group by window_start, window_end, user_id;
