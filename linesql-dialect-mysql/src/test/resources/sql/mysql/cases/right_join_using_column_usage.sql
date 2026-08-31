select o.id, p.paid_at
from app.orders o right join app.payments p using (id)
where p.status = 'PAID';
