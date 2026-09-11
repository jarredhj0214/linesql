SELECT u.id, q.last_amount
FROM public.users u
CROSS JOIN LATERAL (
  SELECT o.amount AS last_amount
  FROM public.orders o
  WHERE o.user_id = u.id
  ORDER BY o.created_at DESC
  LIMIT 1
) q
