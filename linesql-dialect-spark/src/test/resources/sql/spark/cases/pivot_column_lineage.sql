select user_id, small_total, large_total
from (
  select user_id, category, amount
  from ods.orders
) p
pivot (
  sum(amount) as total for category in ('small' as small, 'large' as large)
)
