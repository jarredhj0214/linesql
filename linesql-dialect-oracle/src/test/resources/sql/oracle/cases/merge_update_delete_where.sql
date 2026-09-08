merge into ads.user_summary t
using ods.users_delta s
on (t.user_id = s.id)
when matched then
  update set t.user_name = s.name
  delete where s.is_deleted = 1;
