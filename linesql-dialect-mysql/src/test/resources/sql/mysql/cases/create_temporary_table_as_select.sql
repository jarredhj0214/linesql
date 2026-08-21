create temporary table if not exists mart.tmp_users as
select id as user_id, name
from app.users
