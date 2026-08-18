delete u
from mart.users u
join (
  select id
  from app.deleted_users
) d on u.id = d.id
