select t.aid, t.bid, t.cid
from dwd.events match_recognize (
  partition by user_id
  order by proctime
  measures
    a.id as aid,
    b.id as bid,
    c.id as cid
  one row per match
  after match skip past last row
  pattern (a b c)
  define
    a as name = 'a',
    b as name = 'b',
    c as name = 'c'
) as t;
