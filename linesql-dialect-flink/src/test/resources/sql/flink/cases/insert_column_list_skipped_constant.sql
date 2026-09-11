INSERT INTO ads_user_summary (load_time, uid)
SELECT current_timestamp, u.id
FROM ods_users u
WHERE u.status = 'ACTIVE';
