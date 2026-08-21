load data local infile '/tmp/users.csv'
into table mart.users
fields terminated by ','
(id, name)
set created_at = now();
