select id, name
from dbo.users for system_time between '20260901' and '20260908'
where status = 'ACTIVE';
