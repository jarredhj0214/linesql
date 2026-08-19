with delta as (
  select id, name, updated_at
  from staging.users_delta
  where op = 'U'
)
merge into mart.users t
using delta d
on t.id = d.id
when matched then
  update set name = d.name
when not matched then
  insert (id, name, updated_at) values (d.id, d.name, d.updated_at);
