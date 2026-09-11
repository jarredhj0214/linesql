select e.event_id, j.[key] as item_key, j.value as item_value, j.type as item_type
from dbo.events e
cross apply openjson(e.payload) j
where e.dt = '20260908';
