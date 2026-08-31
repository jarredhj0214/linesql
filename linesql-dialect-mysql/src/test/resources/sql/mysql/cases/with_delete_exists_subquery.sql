with expired_users as (
    select user_id
    from app.sessions
    where expired = 1
)
delete from mart.users u
where exists (
    select 1
    from expired_users e
    where e.user_id = u.id
)
