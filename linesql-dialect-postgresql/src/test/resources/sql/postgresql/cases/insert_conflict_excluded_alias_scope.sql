INSERT INTO mart.accounts (id, balance)
SELECT d.id, d.amount FROM staging.delta AS d
ON CONFLICT (id) DO UPDATE SET balance = (
    SELECT excluded.amount FROM sales.payments AS excluded WHERE excluded.active
);
