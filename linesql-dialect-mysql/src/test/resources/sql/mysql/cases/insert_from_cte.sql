insert into ads.active_users (user_id, user_name)
with active as (
  select id, name from ods.users where status = 'active'
)
select id, name from active
