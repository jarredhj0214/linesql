select
  array_sort(scores, (left_score, right_score) -> left_score > right_score) as sorted_scores
from dwd.player_scores;
