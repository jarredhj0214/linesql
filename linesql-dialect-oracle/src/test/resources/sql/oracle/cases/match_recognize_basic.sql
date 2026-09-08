select user_id, first_amount, last_amount
from ods.user_events
match_recognize (
  partition by user_id
  order by event_time
  measures first(amount) as first_amount, last(amount) as last_amount
  one row per match
  after match skip past last row
  pattern (start_event middle_event* end_event)
  define
    start_event as event_type = 'start',
    end_event as amount > start_event.amount
) mr;
