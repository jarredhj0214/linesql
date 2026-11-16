INSERT INTO mart.accounts (balance)
VALUES (10)
RETURNING OLD.balance AS old_balance, NEW.balance AS new_balance;
