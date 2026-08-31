select
  current_date as run_date,
  current_time as run_time,
  current_timestamp as run_ts,
  utc_timestamp as utc_ts
from app.orders;
