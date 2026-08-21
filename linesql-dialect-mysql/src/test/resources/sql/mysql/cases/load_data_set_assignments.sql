load data local infile '/tmp/users.csv'
into table mart.users
fields terminated by ','
lines terminated by '\n'
ignore 1 lines
(id, name)
set created_at = now(),
    source_file = 'users.csv'
