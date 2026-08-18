delete t
from ads.user_summary t
join (
  select id
  from ods.users_delta
) u on t.user_id = u.id;
