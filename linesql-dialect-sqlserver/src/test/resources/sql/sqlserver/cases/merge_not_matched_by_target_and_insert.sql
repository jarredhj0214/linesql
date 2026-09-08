merge into ads.user_summary as t
using ods.users_delta as s
on t.user_id = s.id
when not matched by target and s.status = 'ACTIVE' then
  insert (user_id, user_name)
  values (s.id, s.name);
