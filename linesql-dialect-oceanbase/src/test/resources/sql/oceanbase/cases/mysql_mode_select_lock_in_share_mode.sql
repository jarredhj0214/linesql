select id, name
from app.users
where status = 'ACTIVE'
lock in share mode
