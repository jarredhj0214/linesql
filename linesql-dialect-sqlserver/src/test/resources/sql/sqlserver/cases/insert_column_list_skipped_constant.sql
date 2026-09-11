INSERT INTO ads.user_summary (load_time, uid)
SELECT GETDATE(), u.id
FROM ods.users u
WHERE u.status = 'ACTIVE';
