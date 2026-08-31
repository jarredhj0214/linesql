select
  event_day,
  site_id,
  pv
from mart.site_access temporary partition(tp1, tp2)
