SELECT j.*
FROM ods.orders AS o
CROSS JOIN LATERAL jsonb_each(o.attributes) AS j
WHERE j.key = 'shop';
