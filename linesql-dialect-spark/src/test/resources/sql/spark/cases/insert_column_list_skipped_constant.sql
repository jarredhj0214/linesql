INSERT INTO ads.user_summary (load_dt, user_id)
SELECT '2026-09-10', u.id
FROM ods.users u
WHERE u.status = 'ACTIVE';
