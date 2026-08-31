create view mart.v_user_union_declared (uid, uname) as
select id, name
from app.users
union all
select user_id, user_name
from app.admins
