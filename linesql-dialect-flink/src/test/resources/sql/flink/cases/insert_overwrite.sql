INSERT OVERWRITE ads.user_summary (user_id, user_name)
SELECT id, name FROM ods.users;
