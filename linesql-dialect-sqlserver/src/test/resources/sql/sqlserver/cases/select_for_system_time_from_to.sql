select id, name
from dbo.users for system_time from '20260901' to '20260908'
where status = 'ACTIVE';
