CREATE TEMP VIEW active_users (user_id)
WITH (security_barrier = true)
AS SELECT u.id FROM public.users AS u WHERE u.active
WITH LOCAL CHECK OPTION;
