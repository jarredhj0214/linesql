insert into mart.users (id, name)
values (1, 'Alice') as new_user
on duplicate key update name = new_user.name;
