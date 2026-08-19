merge into mart.users t
using (
  select id, name, updated_at
  from staging.users_delta
  where op = 'U'
) s
on t.id = s.id
when matched then
  update set name = s.name
when not matched then
  insert (id, name) values (s.id, s.name);
