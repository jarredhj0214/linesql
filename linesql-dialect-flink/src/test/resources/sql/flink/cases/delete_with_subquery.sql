delete from ads_users
where id in (select user_id from ods_inactive_users);
