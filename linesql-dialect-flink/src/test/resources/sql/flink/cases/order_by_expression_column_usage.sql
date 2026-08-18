select
  o.order_id
from ods.orders o
order by coalesce(o.updated_at, o.created_at)
