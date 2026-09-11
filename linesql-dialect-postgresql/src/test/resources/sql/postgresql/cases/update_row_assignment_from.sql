update mart.user_summary t
set (user_name, order_score) = (s.name, s.score + t.bonus_score)
from staging.user_delta s
where t.user_id = s.id;
