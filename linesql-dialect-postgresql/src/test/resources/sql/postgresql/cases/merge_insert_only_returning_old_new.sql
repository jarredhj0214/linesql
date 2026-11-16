MERGE INTO mart.accounts AS a USING staging.delta AS d ON a.id = d.id
WHEN NOT MATCHED THEN INSERT (id, balance) VALUES (d.id, d.balance)
RETURNING OLD.balance AS old_balance, NEW.balance AS new_balance;
