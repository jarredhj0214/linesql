select id
from app.orders
where json_contains(payload, json_quote(sku), '$.skus')
  and json_contains_path(payload, 'one', '$.buyer.id')
