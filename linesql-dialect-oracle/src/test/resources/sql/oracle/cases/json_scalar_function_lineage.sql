select
  e.event_id,
  json_value(e.payload, '$.user.id') as user_id,
  json_query(e.payload, '$.items') as items_json
from ods.events e
where json_exists(e.payload, '$.user.id');
