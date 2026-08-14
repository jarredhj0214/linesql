update dbo.users
set latest_flag = (select max(flag) from ods.user_flags)
where id in (select user_id from ods.active_users);
