select o.id
from app.orders o
where o.amount = any (
    select r.target_amount
    from app.region_targets r
    where r.region = o.region
)
and o.status <> some (
    select b.status
    from app.blocked_status b
    where b.enabled = 1
)
