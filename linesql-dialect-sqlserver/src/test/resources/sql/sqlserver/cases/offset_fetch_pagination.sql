select u.id as user_id
from dbo.users u
order by u.created_at desc
offset 10 rows fetch next 20 rows only;
