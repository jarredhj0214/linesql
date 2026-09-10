select u.id as user_id, lower(u.email) as email_norm
into mart.active_user_snapshot
from public.users u
where u.status = 'ACTIVE';
