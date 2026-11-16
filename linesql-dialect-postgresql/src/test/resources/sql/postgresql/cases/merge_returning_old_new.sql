MERGE INTO mart.accounts AS a USING staging.delta AS d ON a.id = d.id
WHEN MATCHED THEN UPDATE SET balance = d.balance
RETURNING OLD.balance AS old_balance, NEW.balance AS new_balance, d.id AS delta_id;
