select id, name
from app.users
where status = 'ACTIVE'
for update
