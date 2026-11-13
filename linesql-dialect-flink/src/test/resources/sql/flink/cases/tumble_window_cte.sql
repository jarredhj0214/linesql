with recent_orders as (
  select o.order_id, o.event_ts as ts from ods.orders o
)
select w.order_id, w.window_end
from tumble(table recent_orders, descriptor(ts), interval '1' hour) w;
