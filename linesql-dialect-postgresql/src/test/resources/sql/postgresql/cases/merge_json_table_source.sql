MERGE INTO mart.orders AS t
USING JSON_TABLE(
    '[{"id":1,"qty":3}]'::jsonb, '$[*]'
    COLUMNS (id integer PATH '$.id', qty integer PATH '$.qty')
) AS s
ON t.id = s.id
WHEN MATCHED THEN UPDATE SET qty = s.qty
WHEN NOT MATCHED THEN INSERT (id, qty) VALUES (s.id, s.qty)
RETURNING t.id;
