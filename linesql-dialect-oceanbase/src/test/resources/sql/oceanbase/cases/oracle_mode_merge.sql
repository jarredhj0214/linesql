merge into mart.users t
using staging.users_delta s
on (t.id = s.id)
when matched then update set t.name = s.name
when not matched then insert (id, name) values (s.id, s.name)
