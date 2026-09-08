select id, name
from dbo.users for system_time all
where status = 'ACTIVE';
