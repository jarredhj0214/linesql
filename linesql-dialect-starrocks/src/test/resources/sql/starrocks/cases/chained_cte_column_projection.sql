with base as (
  select id as user_id, name
  from ods.users
),
renamed as (
  select user_id as uid, name as user_name
  from base
)
select uid, user_name
from renamed;
