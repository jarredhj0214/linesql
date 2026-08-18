select
  lower(o.region) as region_key,
  count(o.order_id) as order_count
from dbo.orders o
group by lower(o.region)
having count(o.order_id) > 0
