select
  named_struct('user_id', user_id, 'score', total_score) as user_payload
from dwd.user_scores;
