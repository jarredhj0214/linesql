create temporary view session.active_users as
select
  id as user_id,
  email
from ods.users
where status = 'ACTIVE'
