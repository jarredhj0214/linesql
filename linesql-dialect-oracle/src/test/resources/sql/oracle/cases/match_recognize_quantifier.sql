select user_id, high_amount
from ods.user_events
match_recognize (
  partition by user_id
  order by event_time
  measures last(amount) as high_amount
  all rows per match
  after match skip to next row
  pattern (low_event+ high_event{2,3})
  define
    low_event as amount <= 100,
    high_event as amount > 100
) mr;
