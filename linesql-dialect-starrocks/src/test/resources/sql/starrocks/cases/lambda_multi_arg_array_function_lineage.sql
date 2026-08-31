select
  id,
  array_map((score, weight) -> score * weight, scores, weights) as weighted_scores
from dwd.student_scores
