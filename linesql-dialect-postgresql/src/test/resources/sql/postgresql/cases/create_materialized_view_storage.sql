CREATE MATERIALIZED VIEW mart.active_users (user_id)
USING heap WITH (fillfactor = 85) TABLESPACE reporting
AS SELECT u.id FROM public.users AS u WHERE u.active
WITH DATA;
