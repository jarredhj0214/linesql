update ads.user_summary t
set user_name = upper(user_name)
where exists (
  select 1
  from ods.users_delta s
  where s.id = t.user_id
)
