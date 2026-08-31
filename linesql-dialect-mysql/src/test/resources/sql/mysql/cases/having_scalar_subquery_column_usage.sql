select user_id, sum(amount) as total_amount
from app.orders
group by user_id
having sum(amount) > (
  select avg(amount)
  from app.orders_archive
  where status = 'PAID'
);
