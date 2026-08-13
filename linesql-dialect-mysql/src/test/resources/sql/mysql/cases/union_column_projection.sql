select id as user_id
from app.users
union all
select id
from app.admins
