with active_users as (
    select id, name
    from app.users
    where status = 'ACTIVE'
)
insert into mart.active_users(user_id, user_name)
select a.id, a.name
from active_users a
where exists (
    select 1
    from app.sessions s
    where s.user_id = a.id and s.active = 1
)
