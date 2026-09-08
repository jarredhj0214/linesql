select user_id as id
from app.users
intersect distinct
select user_id
from app.active_users
