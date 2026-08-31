alter view ads.v_order_daily as
select
  dt,
  user_id,
  sum(amount) as total_amount
from dwd.orders
where status = 'PAID'
group by dt, user_id;
