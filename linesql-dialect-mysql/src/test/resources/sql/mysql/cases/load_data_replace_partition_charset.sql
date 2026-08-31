load data low_priority infile '/data/users.csv'
replace into table app.users partition (p202608)
character set utf8mb4
fields terminated by ',' enclosed by '"'
lines terminated by '\n'
ignore 1 rows
(id, name, status)
set loaded_at = now()
