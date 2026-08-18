select id as user_id
from ods.users_a
intersect
select user_id
from ods.users_b;
