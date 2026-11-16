DELETE FROM mart.accounts
WHERE inactive
RETURNING WITH (OLD AS removed, NEW AS after_row) removed.*, after_row.id AS new_id;
