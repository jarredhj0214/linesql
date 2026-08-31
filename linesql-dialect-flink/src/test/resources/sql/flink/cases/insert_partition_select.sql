insert overwrite ads.daily_orders partition (dt = '2026-08-31') (user_id, amount)
select user_id, amount
from dwd.orders
where dt = '2026-08-31';
