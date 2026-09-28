update dbo.users
set display_name = s.name
output deleted.display_name, upper(inserted.display_name)
into audit.user_name_changes (old_name, normalized_name)
from staging.user_delta s
where dbo.users.id = s.id;
