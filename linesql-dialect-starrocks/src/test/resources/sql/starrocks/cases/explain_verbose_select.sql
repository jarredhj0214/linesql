explain verbose
select
  u.id,
  count(o.order_id) as order_count
from ods.users u
join dwd.orders o on u.id = o.user_id
group by u.id
