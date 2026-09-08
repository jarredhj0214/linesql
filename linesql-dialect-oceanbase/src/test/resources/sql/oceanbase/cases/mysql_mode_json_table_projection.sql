SELECT
  u.id AS order_id,
  jt.sku,
  jt.qty,
  jt.rn
FROM app.orders u
JOIN JSON_TABLE(
  u.payload,
  '$.items[*]'
  COLUMNS (
    sku VARCHAR(64) PATH '$.sku',
    qty INT PATH '$.qty',
    rn FOR ORDINALITY
  )
) jt ON jt.sku = u.sku
WHERE jt.qty > 0;
