select id, name
from dbo.users for system_time contained in ('20260901', '20260908')
where status = 'ACTIVE';
