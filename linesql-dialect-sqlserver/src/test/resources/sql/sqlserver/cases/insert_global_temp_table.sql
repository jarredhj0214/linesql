insert into ##loaded_users (user_id)
select id
from dbo.users;
