select user_id
from dwd.orders
where dt = date '2024-01-01'
  and created_at >= timestamp '2024-01-01 00:00:00'
