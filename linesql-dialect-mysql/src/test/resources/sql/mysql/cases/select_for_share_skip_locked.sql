select id, name
from app.users
where status = 'ACTIVE'
for share of app.users skip locked
