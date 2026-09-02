select mr.user_id, mr.first_id, mr.last_id
from dwd.events match_recognize (
  partition by user_id
  order by rowtime
  measures
    a.id as first_id,
    b.id as last_id
  one row per match
  after match skip past last row
  pattern (a b+)
  within interval '10' minute
  define
    a as event_type = 'start',
    b as event_type = 'click'
) as mr;
