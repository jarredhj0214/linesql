update ads.user_summary t
set user_name = u.name
from ods.users u
where t.user_id = u.id;
