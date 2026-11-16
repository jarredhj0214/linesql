MERGE INTO mart.accounts AS a USING staging.delta AS d ON a.id = d.id
WHEN MATCHED AND EXISTS (
    SELECT 1 FROM ops.approvals AS q WHERE q.account_id = d.id
)
THEN UPDATE SET balance = d.amount;
