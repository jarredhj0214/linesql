delete t
from ads.user_summary t
join ods.users u on t.user_id = u.id;
