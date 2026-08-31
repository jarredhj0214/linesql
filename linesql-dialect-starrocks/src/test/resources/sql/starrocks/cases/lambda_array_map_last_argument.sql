select
  array_map(scores, x -> x + bonus) as adjusted_scores
from dwd.player_scores;
