insert into ads_user_summary
with q as (
  select id as user_id, name as user_name
  from ods_users
)
select q.user_id, q.user_name
from q;
