alter view mart.active_users as
select
  id as user_id,
  lower(email) as normalized_email
from ods.users
where status = 'ACTIVE'
