update ads.user_summary
set user_name = s.name
output deleted.user_name, inserted.user_name
into @changed_users (old_name, new_name)
from ods.users s
where ads.user_summary.user_id = s.id;
