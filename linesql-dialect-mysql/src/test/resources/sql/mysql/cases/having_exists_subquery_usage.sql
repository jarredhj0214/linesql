select u.region, count(*) as user_count
from app.users u
group by u.region
having exists (
    select 1
    from app.region_acl a
    where a.region = u.region and a.enabled = 1
)
