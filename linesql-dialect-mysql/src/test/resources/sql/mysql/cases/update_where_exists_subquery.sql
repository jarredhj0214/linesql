update mart.users u
set u.status = 'LOCKED'
where exists (
    select 1
    from app.blacklist b
    where b.user_id = u.id and b.enabled = 1
)
order by u.id
limit 100
