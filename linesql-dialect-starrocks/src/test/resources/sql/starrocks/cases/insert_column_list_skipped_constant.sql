INSERT INTO ads.user_summary (load_dt, uid)
SELECT current_date(), u.id
FROM ods.users u
WHERE u.status = 'ACTIVE';
