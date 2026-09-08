update ads.user_summary
set user_name = s.name
output deleted.*, inserted.*
into audit.user_summary_changes
from ads.user_summary t
join ods.users s on t.user_id = s.id
where t.dt = '20260908';
