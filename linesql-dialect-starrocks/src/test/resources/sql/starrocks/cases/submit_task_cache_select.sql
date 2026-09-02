submit task warm_user_cache
as cache select
  user_id,
  sum(amount) as total_amount
from dwd.orders
where dt = '2026-09-02'
group by user_id;
