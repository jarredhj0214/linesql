select s.window_start, s.window_time, s.user_id, s.order_id
from session(
  data => table ods.orders partition by (user_id, region),
  timecol => descriptor(event_ts),
  gap => interval '10' minute
) s;
