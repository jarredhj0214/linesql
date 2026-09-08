select u.id, u.name
from dbo.users u
where u.status = 'ACTIVE'
option (recompile, maxdop 4);
