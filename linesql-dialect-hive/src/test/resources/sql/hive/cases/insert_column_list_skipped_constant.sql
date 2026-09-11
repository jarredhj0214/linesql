INSERT INTO TABLE ads.user_summary (load_dt, uid)
SELECT '2026-09-10', u.id
FROM ods.users u
WHERE u.status = 'ACTIVE';
