create table mart.user_union_declared (
    uid bigint,
    uname varchar(100)
) as
select id, name
from app.users
union all
select user_id, user_name
from app.admins
