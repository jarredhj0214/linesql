create table mart.active_users_replace
replace
select id as user_id, name as user_name
from app.users
where status = 'ACTIVE';
