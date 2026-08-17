select date_add(ds, interval '' - 1 day) as stat_date
from ods.user_events
