DELETE FROM mart.accounts AS a
WHERE a.inactive
RETURNING EXISTS (
    SELECT 1 FROM audit.events AS e WHERE e.account_id = a.id
) AS has_audit;
