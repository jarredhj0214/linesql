UPDATE mart.accounts AS a
SET active = true
WHERE a.state = 1
RETURNING (
    SELECT s.level FROM dim.account_levels AS s WHERE s.account_id = a.id
) AS account_level;
