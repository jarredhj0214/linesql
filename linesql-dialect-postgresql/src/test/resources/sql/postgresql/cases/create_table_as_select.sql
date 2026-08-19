create table mart.active_users as
select id as user_id, lower(email) as email_norm
from public.users
where status = 'ACTIVE'
