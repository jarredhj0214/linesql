SELECT item
FROM dwd.events e
CROSS JOIN UNNEST(JSON_QUERY(e.payload, '$.items' RETURNING ARRAY<STRING>)) AS u(item)
