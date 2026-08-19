insert into mart.users(id, name)
select id, name from staging.users_delta
on conflict (id) do update set name = excluded.name
