select
  u.region,
  count(o.order_id) as order_count
from ods.users u
join [bucket] dwd.orders o on u.id = o.user_id
group by u.region
