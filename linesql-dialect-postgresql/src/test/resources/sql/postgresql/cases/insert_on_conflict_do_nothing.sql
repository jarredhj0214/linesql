insert into mart.users(id, name)
select id, name
from staging.users_delta
where op = 'I'
on conflict (id) do nothing;
