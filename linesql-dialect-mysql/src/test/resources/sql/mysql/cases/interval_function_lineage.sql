select
  date_add(created_at, interval 7 day) as expires_at,
  date_sub(updated_at, interval retry_count hour) as retry_window_start
from app.orders;
