create or replace view mart.v_active_users as
select u.id as user_id,
       u.name
from app.users u
where u.status = 'active'
