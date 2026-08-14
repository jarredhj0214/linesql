insert into ads.user_summary (uid, uname)
select q.user_id, q.user_name
from (
  select id as user_id, name as user_name
  from ods.users
) q;
