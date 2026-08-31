create materialized view mart.mv_active_users
build deferred
refresh manual
as
select
  id as user_id,
  name
from dwd.users
where status = 'active';
