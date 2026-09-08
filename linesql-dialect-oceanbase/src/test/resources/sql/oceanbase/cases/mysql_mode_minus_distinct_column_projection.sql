select user_id as id
from app.users
minus distinct
select user_id
from app.deleted_users
