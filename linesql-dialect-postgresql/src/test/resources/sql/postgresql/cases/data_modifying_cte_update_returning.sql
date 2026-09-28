with changed_users(user_id, new_status) as (
  update mart.users u
  set status = d.status
  from staging.user_delta d
  where u.id = d.id
  returning u.id, u.status
)
select user_id, new_status
from changed_users;
