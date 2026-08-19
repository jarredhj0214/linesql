with paid_orders as (
  select user_id, amount
  from sales.orders
  where status = 'PAID'
)
insert into mart.user_order_summary (user_id, total_amount)
select user_id, amount
from paid_orders
returning user_id;
