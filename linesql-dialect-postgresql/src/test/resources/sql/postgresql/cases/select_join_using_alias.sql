SELECT merged.id AS user_id, u.name, p.plan
FROM public.users AS u
FULL JOIN public.plans AS p USING (id) AS merged
WHERE merged.id > 100;
