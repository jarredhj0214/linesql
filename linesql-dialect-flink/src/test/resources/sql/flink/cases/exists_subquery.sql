SELECT u.id, u.name
FROM ods.users u
WHERE EXISTS (SELECT 1 FROM ods.orders o WHERE o.user_id = u.id);
