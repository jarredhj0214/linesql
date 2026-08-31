select
  scores[1] as first_score,
  element_at(tags, 1) as first_tag
from dwd.user_metrics;
