select id, name
into outfile '/tmp/users.csv'
columns terminated by ',' optionally enclosed by '"'
lines terminated by '\n'
from app.users;
