select id as user_id
from dbo.users_a
intersect
select user_id
from dbo.users_b;
