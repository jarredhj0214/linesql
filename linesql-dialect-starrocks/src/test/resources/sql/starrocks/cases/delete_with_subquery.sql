delete from ads.users
where id in (select user_id from ods.inactive_users);
