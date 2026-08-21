select id, name
from app.users
where status = 'ACTIVE'
into outfile '/tmp/users.csv';
