with order_amounts as (
  select user_id, amount
  from dwd.orders
  where status = 'PAID'
)
update ads.users u
set total_amount = o.amount
from order_amounts o
where u.id = o.user_id;
