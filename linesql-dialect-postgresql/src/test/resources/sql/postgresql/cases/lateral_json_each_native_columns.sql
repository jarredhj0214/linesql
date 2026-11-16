SELECT j.key, j.value
FROM ods.orders AS o
CROSS JOIN LATERAL pg_catalog.jsonb_each(o.attributes) AS j
WHERE j.key = 'shop';
