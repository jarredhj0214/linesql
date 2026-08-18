update ads.user_summary t
set user_name = upper(u.name),
    order_score = u.order_count + t.bonus_count
from ods.users u
where t.user_id = u.id;
