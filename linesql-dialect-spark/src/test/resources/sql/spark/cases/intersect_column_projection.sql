select id as user_id
from ods.users
intersect
select id
from ods.active_users
