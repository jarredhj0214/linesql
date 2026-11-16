SELECT orders.id, (
    SELECT MAX(f.flag) AS inner_flag FROM archive.orders f
    WHERE f.account_id = orders.id
) AS selected_flag
FROM live.accounts orders;
