UPDATE t
SET amount += d.increment, flags &= d.mask
OUTPUT inserted.amount AS new_amount, deleted.flags AS old_flags
FROM dbo.accounts AS t
JOIN staging.account_changes AS d ON t.id = d.id;
