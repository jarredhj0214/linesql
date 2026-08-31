select
  id,
  array_map(x -> x + 1, scores) as next_scores
from dwd.student_scores
