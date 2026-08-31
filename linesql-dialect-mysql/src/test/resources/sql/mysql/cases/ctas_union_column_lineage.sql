create table mart.user_union as
select id as user_id, name as user_name
from app.users
union all
select user_id, user_name
from app.admins
