merge into ads.users t
using ods.user_updates s
on t.id = s.id and s.dt = '2026-08-17'
when matched and s.status = 'ACTIVE' then update set name = s.name
when not matched and s.status = 'NEW' then insert (id, name) values (s.id, s.name)
