select
  u.region,
  count(o.id) as order_count
from app.users u force index for join (idx_user_region)
join app.orders o ignore key for group by (idx_order_region)
  on u.id = o.user_id
where u.status = 'ACTIVE'
group by u.region

