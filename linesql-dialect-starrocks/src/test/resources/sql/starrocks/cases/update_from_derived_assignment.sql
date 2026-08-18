update ads.user_summary t
set user_name = upper(u.name),
    order_score = u.order_count + t.bonus_count
from (
  select id, name, order_count
  from ods.users_delta
) u
where t.user_id = u.id;
