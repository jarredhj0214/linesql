update ads_users
set latest_flag = (select max(flag) from ods_user_flags)
where id in (select user_id from ods_active_users);
