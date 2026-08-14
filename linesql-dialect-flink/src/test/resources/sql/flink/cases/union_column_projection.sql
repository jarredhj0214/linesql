select id as user_id
from ods_users_a
union all
select user_id
from ods_users_b;
