SELECT id
FROM ods.users
WHERE status = 'ACTIVE'
UNION ALL
SELECT id
FROM dwd.admins
WHERE enabled = 1;
