create temporary table tmp_users as
select id as user_id, name as user_name
from app.users
where status = 'ACTIVE';

insert into mart.user_summary (user_id, user_name)
select user_id, user_name
from tmp_users;
