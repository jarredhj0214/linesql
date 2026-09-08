merge into ads.user_summary as t
using ods.users_delta as s
on t.user_id = s.id
when matched and s.status = 'ACTIVE' then
  update set t.user_name = s.name;
