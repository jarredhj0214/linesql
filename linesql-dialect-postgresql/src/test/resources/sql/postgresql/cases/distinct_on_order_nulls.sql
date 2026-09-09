SELECT DISTINCT ON (u.account_id)
       u.account_id,
       u.email AS latest_email
FROM public.users u
WHERE u.status = 'active'
ORDER BY u.account_id, u.updated_at DESC NULLS LAST;
