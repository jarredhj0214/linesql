WITH changed AS (
    UPDATE mart.accounts AS a
    SET balance = d.balance
    FROM staging.delta AS d
    WHERE a.id = d.id
    RETURNING WITH (OLD AS before_row, NEW AS after_row)
        before_row.balance AS old_balance,
        after_row.balance AS new_balance,
        after_row.balance - before_row.balance AS difference
)
SELECT old_balance, new_balance, difference FROM changed;
