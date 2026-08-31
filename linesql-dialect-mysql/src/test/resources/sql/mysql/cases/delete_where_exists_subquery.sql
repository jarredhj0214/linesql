delete from mart.users u
where exists (
    select 1
    from app.blacklist b
    where b.user_id = u.id
)
order by u.id
limit 100
