with delta as (
  select id, name
  from staging.users_delta
  where op = 'U'
)
update mart.users t
set name = d.name
from delta d
where t.id = d.id
returning t.id;
