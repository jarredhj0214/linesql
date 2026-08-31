select /*+ SET_VAR(sort_buffer_size = 262144) NO_RANGE_OPTIMIZATION(o) */
  o.id as order_id,
  o.amount
from app.orders o
where o.status = 'PAID'
