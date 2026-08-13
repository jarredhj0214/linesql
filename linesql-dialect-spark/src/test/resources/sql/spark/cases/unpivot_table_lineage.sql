select *
from mart.user_metrics
unpivot (
  value for metric in (clicks, views)
)
