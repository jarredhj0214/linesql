create temporary table tmp_users as
select id as user_id
from app.users;

drop table tmp_users;

select user_id
from tmp_users;
