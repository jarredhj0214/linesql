SELECT r.sku, r.qty, r.ordinality
FROM ods.orders AS o
CROSS JOIN LATERAL ROWS FROM (
    jsonb_to_record(o.item) AS (sku text),
    jsonb_to_record(o.detail) AS (qty integer)
) WITH ORDINALITY AS r
WHERE r.qty > 0;
