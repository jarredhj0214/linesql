select id
from app.orders
where order_date >= {d '2026-01-01'}
  and cutoff_time < {t '12:00:00'}
  and created_at < {ts '2026-01-02 00:00:00'}

