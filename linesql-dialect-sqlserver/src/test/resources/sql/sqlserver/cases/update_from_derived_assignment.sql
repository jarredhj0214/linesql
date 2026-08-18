update ads.user_summary
set user_name = upper(u.name),
    order_score = u.order_count + user_summary.bonus_count
from (
  select id, name, order_count
  from ods.users_delta
) u
where user_summary.user_id = u.id;
