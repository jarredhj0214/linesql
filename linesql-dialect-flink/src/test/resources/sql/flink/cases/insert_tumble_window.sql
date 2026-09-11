insert into ads_window_orders (window_start, user_id, order_count)
select window_start, user_id, count(order_id) as order_count
from table(tumble(table ods.orders, descriptor(ts), interval '1' hour))
group by window_start, user_id;
