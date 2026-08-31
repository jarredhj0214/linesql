select
  o.order_id,
  o.event_time
from ods.orders o
order by o.event_time desc nulls last, o.order_id asc nulls first
