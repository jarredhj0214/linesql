with paid_orders as (
  select user_id, amount
  from dwd.orders
  where status = 'PAID'
)
insert into ads.user_order_summary (user_id, total_amount)
select user_id, amount
from paid_orders;
