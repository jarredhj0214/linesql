select
  o.order_id
from ods.orders o
where o.status = 'PAID'
order by o.created_at desc
limit 10, 100;
