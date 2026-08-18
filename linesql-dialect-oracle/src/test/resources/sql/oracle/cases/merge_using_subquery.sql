merge into ads.user_summary t
using (
  select id, name, order_count
  from ods.users_delta
) s
on (t.user_id = s.id)
when matched then update set user_name = s.name
when not matched then insert (user_id, user_name)
                      values (s.id, s.name)
