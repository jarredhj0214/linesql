insert into app.user_stats(user_id, total_amount, updated_at)
select user_id, sum(amount) as total_amount, max(updated_at) as updated_at
from app.orders
where status = 'PAID'
group by user_id
on duplicate key update
  total_amount = values(total_amount),
  updated_at = values(updated_at)
