update ads.users
set name = (
  select max(name)
  from ods.user_updates
)
where id in (
  select id
  from ods.active_users
)
