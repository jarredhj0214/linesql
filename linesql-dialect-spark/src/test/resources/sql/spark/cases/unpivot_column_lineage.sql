select user_id, metric, value
from mart.user_metrics
unpivot (
  value for metric in (clicks, views)
)
