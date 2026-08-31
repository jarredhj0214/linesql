select
  user_id,
  max(score) over (
    partition by user_id
    order by event_time
    rows between unbounded preceding and current row
  ) as max_score
from ods.user_scores
