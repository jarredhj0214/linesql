SELECT
  jt.sku,
  jt.attr_name
FROM app.orders u,
JSON_TABLE(
  u.payload,
  '$.items[*]'
  COLUMNS (
    sku VARCHAR(64) PATH '$.sku',
    NESTED PATH '$.attrs[*]' COLUMNS (
      attr_name VARCHAR(64) PATH '$.name'
    )
  )
) AS jt
WHERE jt.attr_name IS NOT NULL;
