INSERT INTO mart.accounts (id, balance)
SELECT d.id, d.balance FROM staging.delta AS d
ON CONFLICT (id) DO UPDATE SET balance = excluded.balance
RETURNING OLD.balance AS before_balance, NEW.balance AS after_balance;
