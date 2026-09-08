select id as user_id
from ods.users_a
minus
select user_id
from ods.deleted_users
