MERGE INTO mart.totals AS t
USING (
    SELECT e.id, e.active, e.amount
    FROM ods.events AS e
) AS src
ON t.id = src.id
WHEN NOT MATCHED BY SOURCE AND active THEN
    UPDATE SET amount = amount + 1;
