select
  created_at + interval grace_days day as grace_deadline,
  expired_at - interval retry_hours hour as retry_start_at,
  interval buffer_minutes minute + updated_at as buffered_updated_at
from app.subscriptions
where billing_date + interval billing_grace_days day >= current_date

