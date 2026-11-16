INSERT INTO mart.accounts AS a (id, balance)
SELECT d.id, d.amount FROM staging.delta AS d
ON CONFLICT (id) DO UPDATE SET balance = (
    SELECT p.amount FROM sales.payments AS p WHERE p.account_id = excluded.id
)
WHERE EXISTS (SELECT 1 FROM ops.approvals AS q WHERE q.account_id = a.id);
