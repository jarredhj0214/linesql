alter algorithm = merge view mart.active_users (user_id, user_name) as
select id, name
from app.users
where status = 'ACTIVE'
with cascaded check option;
