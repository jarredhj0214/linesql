select
  current_date() as run_date,
  current_time() as run_time,
  current_timestamp() as run_ts,
  utc_date() as utc_date_value,
  utc_time() as utc_time_value,
  utc_timestamp() as utc_ts
from app.orders
where created_at < current_timestamp();
