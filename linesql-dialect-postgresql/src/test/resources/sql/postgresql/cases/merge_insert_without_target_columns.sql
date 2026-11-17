MERGE INTO mart.orders AS t
USING ods.delta AS s
ON t.id = s.id
WHEN NOT MATCHED AND active THEN
    INSERT VALUES (id, base_amount + tax_amount);
