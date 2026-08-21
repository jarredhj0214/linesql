create algorithm = merge sql security invoker view mart.v_active_users as
select id as user_id, name
from app.users
where status = 'ACTIVE'
with cascaded check option
