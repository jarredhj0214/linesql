insert into ads.user_summary (user_id, user_name)
select t.id, t.name from (select id, name from ods.users) t
