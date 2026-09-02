submit task async_insert_with_properties
properties (
  "session.enable_profile" = "true",
  "session.insert_timeout" = "10000"
)
as insert into ads.user_order_count(user_id, order_count)
select
  user_id,
  count(order_id) as order_count
from dwd.orders
group by user_id;
