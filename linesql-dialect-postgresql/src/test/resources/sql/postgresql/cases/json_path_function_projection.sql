select
  e.id,
  jsonb_path_query_first(e.payload, '$.user.name') as user_name,
  jsonb_path_exists(e.payload, '$.items[*] ? (@.price > 100)') as has_expensive_item
from app.events e
where jsonb_path_exists(e.payload, '$.user.id');
