MERGE INTO mart.accounts AS a USING staging.delta AS d ON a.id = d.id
WHEN MATCHED THEN UPDATE SET balance = d.amount
RETURNING *;
