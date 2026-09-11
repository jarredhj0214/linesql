select
  e.event_id,
  json_value(e.payload, '$.user.id') as user_id,
  json_query(e.payload, '$.items') as items_json
from dbo.events e
where isjson(e.payload) = 1;
