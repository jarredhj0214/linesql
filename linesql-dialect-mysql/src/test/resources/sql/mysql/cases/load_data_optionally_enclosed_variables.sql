load data infile '/tmp/users.csv'
into table mart.users
fields terminated by ',' optionally enclosed by '"' escaped by '\\'
lines starting by '' terminated by '\n'
ignore 1 rows
(@raw_id, name)
set id = cast(@raw_id as unsigned), loaded_at = current_timestamp;
