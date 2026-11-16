SELECT e.item, e.ordinality
FROM ods.orders AS o
CROSS JOIN LATERAL jsonb_array_elements_text(o.payload) WITH ORDINALITY AS e(item)
WHERE e.item <> '';
