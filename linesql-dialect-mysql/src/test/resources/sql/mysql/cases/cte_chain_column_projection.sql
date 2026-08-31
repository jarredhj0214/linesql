with base_users as (
  select id, name
  from app.users
  where status = 'ACTIVE'
),
renamed_users as (
  select id as user_id, upper(name) as user_name
  from base_users
)
select user_id, user_name
from renamed_users;
