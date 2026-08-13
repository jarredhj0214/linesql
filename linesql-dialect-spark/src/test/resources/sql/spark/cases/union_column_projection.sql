select id as user_id, name
from ods.users
union all
select id, name
from ods.admins
