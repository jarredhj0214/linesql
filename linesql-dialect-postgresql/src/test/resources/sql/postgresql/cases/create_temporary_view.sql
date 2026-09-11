create temporary view tmp_active_users as
select id as user_id, email
from public.users
where status = 'ACTIVE';
