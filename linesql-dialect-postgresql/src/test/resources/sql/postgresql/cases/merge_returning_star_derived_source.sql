MERGE INTO mart.accounts AS a
USING (SELECT d.id, d.amount AS balance FROM staging.delta AS d) AS s
ON a.id = s.id
WHEN MATCHED THEN UPDATE SET balance = s.balance
RETURNING *;
