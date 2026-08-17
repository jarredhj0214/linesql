update ads.users u
set last_amount = o.amount
from dwd.orders o
where u.id = o.user_id and o.dt = '2026-08-17'
