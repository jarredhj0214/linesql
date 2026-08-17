SELECT id
FROM ods_users
WHERE status = 'ACTIVE'
UNION ALL
SELECT id
FROM dwd_admins
WHERE enabled = 1;
