insert into mart.user_summary (user_id, user_name, order_score)
select id, name, score
from staging.user_delta
on conflict (user_id) do update
set (user_name, order_score) = (excluded.user_name, excluded.order_score);
