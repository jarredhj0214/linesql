select
  ifnull(nickname, name) as display_name,
  date_trunc('day', event_time) as event_day
from ods.user_events
