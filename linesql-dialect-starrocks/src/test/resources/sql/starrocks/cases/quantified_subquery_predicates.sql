select user_id
from dwd.orders o
where amount > all (
  select limit_amount
  from dim.region_limits r
  where r.region = o.region
)
and status = any (
  select allowed_status
  from dim.allowed_status s
  where s.enabled is true
)
