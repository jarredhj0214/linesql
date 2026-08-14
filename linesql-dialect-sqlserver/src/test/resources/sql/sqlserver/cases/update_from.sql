update ads.user_summary
set user_name = u.name
from ods.users u
where user_summary.user_id = u.id;
