MERGE INTO mart.accounts AS a USING staging.delta AS d ON a.id = d.id
WHEN MATCHED THEN UPDATE SET balance = (
    SELECT max(p.amount) FROM sales.payments AS p WHERE p.account_id = d.id
);
