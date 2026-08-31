select
  jt.sku,
  jt.attr_name
from app.orders u,
json_table(
  u.payload,
  '$.items[*]'
  columns (
    sku varchar(64) path '$.sku',
    nested path '$.attrs[*]' columns (
      attr_name varchar(64) path '$.name'
    )
  )
) as jt
where jt.attr_name is not null
