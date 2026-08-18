update ads.user_summary
set user_name = upper(u.name),
    order_score = u.order_count + user_summary.bonus_count
from ods.users u
where user_summary.user_id = u.id;
