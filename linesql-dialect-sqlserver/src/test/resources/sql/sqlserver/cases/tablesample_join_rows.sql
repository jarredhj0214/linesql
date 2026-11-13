select e.event_id, u.user_name
from dbo.events e
join dbo.users u tablesample (100 rows) on e.user_id = u.user_id;
