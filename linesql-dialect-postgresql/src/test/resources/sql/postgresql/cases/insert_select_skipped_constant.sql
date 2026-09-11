INSERT INTO mart.user_summary (load_time, user_id)
SELECT now(), u.id
FROM public.users u
WHERE u.status = 'ACTIVE';
