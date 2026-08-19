with paid_orders as (
  select user_id, amount
  from dbo.orders
  where status = 'PAID'
)
insert into dbo.user_order_summary (user_id, total_amount)
select user_id, amount
from paid_orders;
