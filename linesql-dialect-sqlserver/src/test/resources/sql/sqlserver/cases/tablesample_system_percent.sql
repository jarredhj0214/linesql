select e.event_id
from dbo.events as e tablesample system (10 percent) repeatable (42) with (nolock)
where e.active = 1;
