insert overwrite table ads.daily_user_metrics
partition (dt = '2026-09-28') if not exists
select
  u.id as user_id,
  sum(u.amount) as total_amount
from ods.user_events u
group by u.id
