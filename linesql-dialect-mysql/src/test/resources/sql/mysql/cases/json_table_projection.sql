select
  u.id as order_id,
  jt.sku,
  jt.qty,
  jt.rn
from app.orders u
join json_table(
  u.payload,
  '$.items[*]'
  columns (
    sku varchar(64) path '$.sku',
    qty int path '$.qty',
    rn for ordinality
  )
) jt on jt.sku = u.sku
where jt.qty > 0
