select id as user_id
from app.users
intersect
select id
from app.admins
