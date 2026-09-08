UPDATE mart.users u
SET u.status = 'LOCKED'
WHERE EXISTS (
    SELECT 1
    FROM app.blacklist b
    WHERE b.user_id = u.id AND b.enabled = 1
)
ORDER BY u.id
LIMIT 100;
