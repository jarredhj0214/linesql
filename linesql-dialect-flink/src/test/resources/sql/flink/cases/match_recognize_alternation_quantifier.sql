select mr.user_id, mr.matched_id
from dwd.events match_recognize (
  partition by user_id
  order by rowtime
  measures
    c.id as matched_id
  all rows per match
  after match skip to next row
  pattern ((a | b) c{2,3})
  within interval '30' minute
  define
    a as event_type = 'view',
    b as event_type = 'click',
    c as amount > 0
) as mr;
