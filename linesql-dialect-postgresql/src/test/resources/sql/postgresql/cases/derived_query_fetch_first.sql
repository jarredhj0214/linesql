select recent.customer_id, recent.amount
from (
  select o.customer_id, o.amount
  from mart.orders o
  where o.status = 'PAID'
  order by o.created_at desc
  fetch first 20 rows only
) recent
where recent.amount > 100
