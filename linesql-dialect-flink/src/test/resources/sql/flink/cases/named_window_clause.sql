select
  user_id,
  sum(amount) over w as rolling_amount
from dwd.orders
window w as (
  partition by user_id
  order by rowtime
  rows between 3 preceding and current row
);
