WITH changed AS (
    INSERT INTO mart.accounts (id, balance)
    SELECT d.id, d.original FROM staging.delta AS d
    ON CONFLICT (id) DO UPDATE SET balance = (
        SELECT p.amount FROM sales.payments AS p WHERE p.account_id = excluded.id
    )
    RETURNING OLD.balance AS before_balance, NEW.balance AS after_balance
)
SELECT before_balance, after_balance FROM changed;
