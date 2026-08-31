select id
from app.orders
where created_at >= timestamp '2026-08-01 00:00:00'
  and order_date = date '2026-08-01'
  and cutoff_time < time '12:30:00';
