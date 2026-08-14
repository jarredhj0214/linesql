MERGE INTO ads.user_summary t
USING ods.users s
ON t.user_id = s.id
WHEN MATCHED THEN UPDATE SET user_name = s.name
WHEN NOT MATCHED THEN INSERT (user_id, user_name) VALUES (s.id, s.name)
