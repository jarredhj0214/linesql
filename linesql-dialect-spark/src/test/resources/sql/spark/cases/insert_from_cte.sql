insert into table ads.user_summary (user_id, user_name)
with base as (
  select id as user_id, name
  from ods.users
)
select user_id, name
from base
