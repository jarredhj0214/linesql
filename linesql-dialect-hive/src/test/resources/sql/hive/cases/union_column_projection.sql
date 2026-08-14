select id as user_id
from ods.users_a
union all
select user_id
from ods.users_b;
