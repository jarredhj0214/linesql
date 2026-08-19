with q as (
  select id as user_id, name from public.users
)
select q.user_id, q.name from q
