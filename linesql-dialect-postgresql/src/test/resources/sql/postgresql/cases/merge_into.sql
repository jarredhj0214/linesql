merge into mart.users t
using staging.users_delta s
on t.id = s.id
when matched and s.op = 'U' then
  update set name = s.name, updated_at = s.updated_at
when not matched then
  insert (id, name, updated_at) values (s.id, s.name, s.updated_at);
