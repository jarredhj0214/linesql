insert into mart.users
set id = (select id from app.users_delta where status = 'active'),
    name = 'anonymous'
as new_user(new_id, new_name)
on duplicate key update updated_id = new_user.new_id,
                        name = concat(name, new_user.new_name);
