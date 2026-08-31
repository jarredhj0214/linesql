select
  t.student,
  s.score
from dwd.tests t
cross join lateral unnest(t.scores) as s(score)
