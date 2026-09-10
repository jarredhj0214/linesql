create table mart.active_users_empty as
select u.id as user_id, lower(u.email) as email_norm
from public.users u
where u.status = 'ACTIVE'
with no data;
