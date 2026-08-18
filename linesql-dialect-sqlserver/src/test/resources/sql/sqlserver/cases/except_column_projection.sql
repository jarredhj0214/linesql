select id as user_id
from dbo.users_a
except
select user_id
from dbo.users_b;
