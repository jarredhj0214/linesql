select id as user_id
from app.users
except
select id
from app.admins
