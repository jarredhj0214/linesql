select user_id, half, sales, expenses
from mart.quarterly_metrics
unpivot (
  (sales, expenses) for half in (
    (q1_sales, q1_expenses) as h1,
    (q2_sales, q2_expenses) as h2
  )
)
