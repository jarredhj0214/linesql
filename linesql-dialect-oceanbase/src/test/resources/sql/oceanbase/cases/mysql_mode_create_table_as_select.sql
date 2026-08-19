create table mart.active_users as
select id as user_id, lower(email) as email_norm
from app.users
where status = 'ACTIVE'
