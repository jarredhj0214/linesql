insert into ads_user_summary (uid)
select id from ods_users_a
intersect
select user_id from ods_users_b;
