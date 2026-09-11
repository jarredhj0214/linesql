table app.users
union all
select id, name
from app.admin_users;
