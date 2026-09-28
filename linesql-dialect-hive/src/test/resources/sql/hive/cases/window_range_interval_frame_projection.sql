select sum(o.amount) over (
  partition by o.user_id
  order by o.event_time
  range between interval 7 day preceding and current row
) as seven_day_amount
from ods.orders o
