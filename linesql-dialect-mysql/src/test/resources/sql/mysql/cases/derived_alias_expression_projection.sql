select concat(q.user_name, '-', q.region_key) as display_name
from (
    select name as user_name, lower(region) as region_key
    from app.users
    where status = 'ACTIVE'
) q
where q.region_key = 'north'
