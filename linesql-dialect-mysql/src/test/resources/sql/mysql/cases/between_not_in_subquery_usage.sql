select id
from app.orders
where amount between min_amount and max_amount
  and status not in ('CANCELLED', 'REFUNDED')
  and region not in (
      select region
      from app.region_acl
      where enabled = 0
  )
