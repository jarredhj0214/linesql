SELECT u.id, u.name
FROM app.users u
WHERE u.id IN (
    SELECT user_id
    FROM app.active_sessions
);
