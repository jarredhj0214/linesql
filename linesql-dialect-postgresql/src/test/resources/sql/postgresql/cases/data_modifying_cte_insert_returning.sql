with inserted_users(user_id, user_name) as (
  insert into mart.users(id, name)
  select s.id, s.name
  from staging.new_users s
  where s.enabled = true
  returning id, name
)
select user_id, user_name
from inserted_users;
