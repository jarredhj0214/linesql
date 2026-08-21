create table mart.active_users as
with active as (
  select id, name
  from ods.users
  where status = 'ACTIVE'
)
select id as user_id, name
from active;
