insert into ads.user_summary (uid)
select id from ods.users_a
except
select user_id from ods.users_b;
