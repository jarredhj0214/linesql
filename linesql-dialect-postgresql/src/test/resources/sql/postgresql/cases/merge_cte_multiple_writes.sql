WITH changed AS (
    MERGE INTO mart.accounts AS a USING staging.delta AS d ON a.id = d.id
    WHEN MATCHED THEN UPDATE SET balance = d.adjusted
    WHEN NOT MATCHED THEN INSERT (id, balance) VALUES (d.id, d.original)
    RETURNING OLD.balance AS before_balance, NEW.balance AS after_balance
)
SELECT before_balance, after_balance FROM changed;
