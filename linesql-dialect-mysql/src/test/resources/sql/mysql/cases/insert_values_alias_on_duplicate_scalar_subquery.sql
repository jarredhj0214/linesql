insert into mart.users (id, name)
values ((select id from app.users where status = 'ACTIVE' limit 1), 'Alice') as new_user
on duplicate key update id = new_user.id
