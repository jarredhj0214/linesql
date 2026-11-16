SELECT f.field, f.value, f.qty, f.ordinality
FROM ods.orders AS o
CROSS JOIN LATERAL ROWS FROM (
    pg_catalog.jsonb_each_text(o.attributes),
    jsonb_to_record(o.payload) AS (qty integer)
) WITH ORDINALITY AS f(field)
WHERE f.qty > 0;
