select e.event_id, j.user_id, j.items_json
from dbo.events e
cross apply openjson(e.payload, '$.orders')
with (
  user_id bigint '$.userId',
  items_json nvarchar(max) '$.items' as json
) j
where isjson(j.items_json) = 1;
