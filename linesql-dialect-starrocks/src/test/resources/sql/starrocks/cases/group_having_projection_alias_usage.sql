select
  date_trunc('day', event_time) as event_day,
  sum(amount) as total_amount
from dwd.orders
group by event_day
having total_amount > 100;
