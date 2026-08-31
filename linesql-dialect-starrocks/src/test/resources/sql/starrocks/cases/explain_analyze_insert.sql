explain analyze
insert into ads.daily_orders(user_id, amount)
select
  user_id,
  sum(amount) as amount
from dwd.orders
where dt = '2026-08-31'
group by user_id;
