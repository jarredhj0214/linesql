WITH removed (customer_id, last_seen) AS (
    DELETE FROM public.users u WHERE u.inactive RETURNING u.id, u.updated_at
)
TABLE removed ORDER BY 2;
