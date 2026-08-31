select
  get_json_string(e.payload, '$.user_id') as user_id,
  json_query(e.payload, '$.items') as items_json,
  parse_json(e.payload) as payload_json
from ods.events e
where json_exists(e.payload, '$.user_id')
