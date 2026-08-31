create view ads.v_secure_orders (
  dt comment 'partition date',
  user_id comment 'user identifier',
  total_amount comment 'paid amount'
)
comment 'secure paid orders'
security invoker
as
select dt, user_id, sum(amount) as total_amount
from dwd.orders
where status = 'PAID'
group by dt, user_id;
