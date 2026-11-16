SELECT jt.customer
FROM ods.orders o CROSS JOIN JSON_TABLE(
    o.payload::jsonb, '$' COLUMNS (customer text PATH '$.customer')
) jt
WHERE jt.customer IS NOT NULL;
