create materialized view mart.mv_active_users_loaded as
select id as user_id, email
from public.users
where status = 'ACTIVE'
with data;
