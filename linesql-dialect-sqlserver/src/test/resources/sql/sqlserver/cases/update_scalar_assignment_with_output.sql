UPDATE dbo.users
SET latest_flag = (SELECT MAX(f.flag) FROM ods.user_flags f WHERE f.user_id = dbo.users.id),
    active = s.active
OUTPUT inserted.latest_flag AS new_flag
FROM ods.active_users s
WHERE dbo.users.id = s.user_id;
