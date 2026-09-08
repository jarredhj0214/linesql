select /*+ LEADING(u o) INDEX(u idx_users_id) */
  u.id as user_id,
  o.amount
from app.users u
join app.orders o on u.id = o.user_id
where o.status = 'PAID'
