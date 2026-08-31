insert into mart.users (id, name, status)
values (1, 'Alice', 'NEW')
on duplicate key update
    status = default(status),
    name = values(name)
