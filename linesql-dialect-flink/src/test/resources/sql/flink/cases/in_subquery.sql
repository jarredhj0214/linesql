SELECT u.id, u.name
FROM ods.users u
WHERE u.id IN (SELECT user_id FROM ods.active_sessions);
