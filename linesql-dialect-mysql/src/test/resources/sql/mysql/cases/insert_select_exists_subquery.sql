insert into mart.active_users(user_id, name)
select u.id, u.name
from app.users u
where exists (
    select 1
    from app.sessions s
    where s.user_id = u.id and s.active = 1
)
order by u.id
limit 100
