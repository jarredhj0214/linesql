with base as (
  select id as user_id, name
  from ods.users
)
select user_id, name
from base
