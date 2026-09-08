insert into mart.users
set id = (select max(id) from app.users_delta where status = 'NEW'),
    name = 'anonymous'
on duplicate key update
    name = values(name),
    updated_id = values(id);
