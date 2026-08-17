SELECT id
FROM app.users
WHERE status = 'ACTIVE'
UNION ALL
SELECT id
FROM app.admins
WHERE enabled = 1;
