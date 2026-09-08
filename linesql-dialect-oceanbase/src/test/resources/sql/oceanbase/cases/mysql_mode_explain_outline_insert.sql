explain outline insert into mart.active_users (user_id, user_name)
select id, name
from app.users
where status = 'ACTIVE'
