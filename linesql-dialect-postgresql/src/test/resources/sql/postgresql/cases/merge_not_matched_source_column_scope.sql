MERGE INTO mart.totals AS t
USING ods.events AS src
ON t.id = src.id
WHEN NOT MATCHED BY TARGET AND active THEN
    INSERT (id, amount) VALUES (id, amount);
