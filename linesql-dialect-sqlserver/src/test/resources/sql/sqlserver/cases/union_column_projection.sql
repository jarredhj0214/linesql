select id as user_id
from dbo.users_a
union all
select user_id
from dbo.users_b;
