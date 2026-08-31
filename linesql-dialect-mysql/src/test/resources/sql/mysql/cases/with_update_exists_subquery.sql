with blocked_users as (
    select user_id
    from app.blacklist
    where enabled = 1
)
update mart.users u
set u.status = 'LOCKED'
where exists (
    select 1
    from blocked_users b
    where b.user_id = u.id
)
