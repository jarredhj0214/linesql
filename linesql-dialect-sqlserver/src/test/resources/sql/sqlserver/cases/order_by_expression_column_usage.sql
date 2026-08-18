select
  o.order_id
from dbo.orders o
order by coalesce(o.updated_at, o.created_at)
