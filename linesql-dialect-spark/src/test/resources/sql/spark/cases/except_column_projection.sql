select id as user_id
from ods.users
except
select id
from ods.deleted_users
