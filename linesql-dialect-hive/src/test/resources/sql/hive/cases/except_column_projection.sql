select id as user_id
from ods.users_a
except
select user_id
from ods.users_b;
