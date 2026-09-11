explain verbose
update mart.users t
set name = s.name
from staging.users_delta s
where t.id = s.id
