load data local infile '/tmp/users.csv'
into table mart.users
columns terminated by ',' optionally enclosed by '"'
lines terminated by '\n'
(id, name);
