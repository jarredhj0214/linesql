create definer = current_user() sql security definer view mart.v_active_users as
select id as user_id, name
from app.users
where status = 'ACTIVE';
