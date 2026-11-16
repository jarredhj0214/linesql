INSERT INTO mart.accounts (id, balance, status)
SELECT d.id, d.amount, d.state FROM staging.delta AS d
ON CONFLICT (id) DO UPDATE SET (balance, status) = (
    SELECT p.amount, excluded.status FROM sales.payments AS p
    WHERE p.account_id = excluded.id
);
