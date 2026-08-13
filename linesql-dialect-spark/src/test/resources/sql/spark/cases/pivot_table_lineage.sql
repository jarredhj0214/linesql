select *
from (
  select user_id, category, amount
  from ods.orders
) p
pivot (
  sum(amount) for category in ('small' as small, 'large' as large)
)
