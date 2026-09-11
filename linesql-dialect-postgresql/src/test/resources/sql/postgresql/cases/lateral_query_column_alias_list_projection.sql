SELECT q.user_id
FROM LATERAL (
  SELECT id
  FROM public.users
) AS q(user_id)
