WITH blocked_users AS (
    SELECT user_id
    FROM app.blacklist
    WHERE enabled = 1
)
UPDATE mart.users u
SET u.status = 'LOCKED'
WHERE EXISTS (
    SELECT 1
    FROM blocked_users b
    WHERE b.user_id = u.id
);
