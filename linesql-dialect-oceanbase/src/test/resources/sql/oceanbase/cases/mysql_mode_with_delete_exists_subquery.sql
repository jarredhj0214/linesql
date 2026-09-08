WITH expired_users AS (
    SELECT user_id
    FROM app.sessions
    WHERE expired = 1
)
DELETE FROM mart.users u
WHERE EXISTS (
    SELECT 1
    FROM expired_users e
    WHERE e.user_id = u.id
);
