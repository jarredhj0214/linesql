DELETE FROM mart.users u
WHERE EXISTS (
    SELECT 1
    FROM app.blacklist b
    WHERE b.user_id = u.id
)
ORDER BY u.id
LIMIT 100;
