explain
insert into ads.user_orders(user_id, order_count)
select
  user_id,
  count(order_id) as order_count
from dwd.orders
group by user_id
