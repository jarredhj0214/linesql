insert into ads_hop_orders (window_start, window_end, user_id, total_amount)
select window_start, window_end, user_id, sum(amount) as total_amount
from table(
  hop(table dwd.orders, descriptor(rowtime), interval '5' minute, interval '1' hour)
)
group by window_start, window_end, user_id;
