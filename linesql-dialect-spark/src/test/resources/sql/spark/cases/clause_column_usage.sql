select
  u.id,
  count(o.order_id) as order_count
from ods.users u
join dwd.orders o on u.id = o.user_id
where u.status = 'active' and o.pay_amount > 0
group by u.id
having count(o.order_id) > 1
order by order_count desc, u.id
