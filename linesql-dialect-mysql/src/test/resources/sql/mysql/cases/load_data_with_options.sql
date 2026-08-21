load data local infile '/tmp/users.csv'
into table mart.users
fields terminated by ',' enclosed by '"'
lines terminated by '\n'
ignore 1 lines
(id, name)
