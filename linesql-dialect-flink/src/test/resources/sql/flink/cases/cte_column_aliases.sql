with base(user_id, user_name) as (
  select id, name
  from ods_users
)
select user_id, user_name
from base;
