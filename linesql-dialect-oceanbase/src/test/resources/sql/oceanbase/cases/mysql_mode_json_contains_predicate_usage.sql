SELECT id
FROM app.orders
WHERE JSON_CONTAINS(payload, JSON_QUOTE(sku), '$.skus')
  AND JSON_CONTAINS_PATH(payload, 'one', '$.buyer.id');
