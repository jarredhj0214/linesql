select id, status
from app.orders
where dt = '2026-08-28'
order by field(status, 'NEW', 'PAID', 'DONE'), coalesce(updated_at, created_at)
