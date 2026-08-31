select
  u.id as user_id,
  r.region_name
from app.users u
cross join dim.regions r
where u.region_id = r.id
