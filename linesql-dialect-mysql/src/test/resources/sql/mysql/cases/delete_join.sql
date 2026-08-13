delete u
from mart.users u
join app.deleted_users d on u.id = d.id
