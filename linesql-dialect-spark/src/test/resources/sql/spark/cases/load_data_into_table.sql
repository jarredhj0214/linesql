load data local inpath '/tmp/users.csv'
overwrite into table ods.users
partition (dt = '2026-08-13')
