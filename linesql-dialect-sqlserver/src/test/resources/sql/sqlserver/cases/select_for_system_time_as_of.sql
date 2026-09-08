select u.id, u.name
from dbo.users for system_time as of '20260908' u
where u.status = 'ACTIVE';
