select id, name
into dumpfile '/tmp/users.bin'
from app.users
where status = 'ACTIVE';
