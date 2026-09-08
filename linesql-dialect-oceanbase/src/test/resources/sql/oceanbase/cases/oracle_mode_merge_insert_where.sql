merge into ads.user_summary t
using ods.users_delta s
on (t.user_id = s.id)
when not matched then
  insert (user_id, user_name)
  values (s.id, s.name)
  where s.status = 'ACTIVE';
