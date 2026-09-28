insert into dbo.users(id, display_name)
output inserted.id as user_id,
       inserted.display_name as user_name
select s.id, s.name
from staging.new_users s
where s.enabled = 1;
