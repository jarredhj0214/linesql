select id, name
into outfile '/tmp/users.csv'
fields terminated by ','
lines terminated by '\n'
from app.users
where status = 'ACTIVE';
