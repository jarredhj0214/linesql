select id::text as user_id_text, lower(email) as email_norm
from public.users
where email ilike '%@example.com'
