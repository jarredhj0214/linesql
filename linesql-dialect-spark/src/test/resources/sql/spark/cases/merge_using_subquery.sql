merge into ads.users t
using (
  select id, name
  from ods.users_delta
) s
on t.id = s.id
when matched then update set name = s.name
when not matched then insert (id, name) values (s.id, s.name)
