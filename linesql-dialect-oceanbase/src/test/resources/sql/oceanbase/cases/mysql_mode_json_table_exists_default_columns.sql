select jt.sku, jt.has_discount
from app.orders o,
json_table(
  o.payload,
  '$.items[*]' columns (
    sku varchar(64) path '$.sku' default 'UNKNOWN' on empty,
    has_discount int exists path '$.discount'
  )
) jt;
