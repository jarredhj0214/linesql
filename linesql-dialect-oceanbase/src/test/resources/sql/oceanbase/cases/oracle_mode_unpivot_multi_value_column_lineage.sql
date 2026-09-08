select metric, current_value, previous_value
from sales.monthly
unpivot (
  (current_value, previous_value)
  for metric in (
    (jan_amount, jan_prev_amount) as 'JAN',
    (feb_amount, feb_prev_amount) as 'FEB'
  )
)
