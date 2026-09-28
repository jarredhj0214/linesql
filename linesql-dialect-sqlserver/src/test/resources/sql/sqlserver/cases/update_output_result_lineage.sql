update dbo.users u
set display_name = s.name
output deleted.display_name as old_name,
       inserted.display_name as new_name
from staging.user_delta s
where u.id = s.id;
